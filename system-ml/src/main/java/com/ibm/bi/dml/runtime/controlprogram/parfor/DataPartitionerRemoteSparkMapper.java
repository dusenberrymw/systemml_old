/**
 * (C) Copyright IBM Corp. 2010, 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.ibm.bi.dml.runtime.controlprogram.parfor;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.hadoop.io.Writable;
import org.apache.spark.api.java.function.PairFlatMapFunction;

import com.ibm.bi.dml.runtime.DMLRuntimeException;
import com.ibm.bi.dml.runtime.DMLUnsupportedOperationException;
import com.ibm.bi.dml.runtime.controlprogram.ParForProgramBlock.PDataPartitionFormat;
import com.ibm.bi.dml.runtime.controlprogram.parfor.util.PairWritableBlock;
import com.ibm.bi.dml.runtime.matrix.data.InputInfo;
import com.ibm.bi.dml.runtime.matrix.data.MatrixBlock;
import com.ibm.bi.dml.runtime.matrix.data.MatrixIndexes;
import com.ibm.bi.dml.runtime.matrix.data.OutputInfo;

import scala.Tuple2;

/**
 * NOTE: for the moment we only support binary block here
 * TODO extend impl for binarycell and textcell	
 * 
 * Interface of Writable output in order to support both PairWritableBlock and PairWritableCell.
 * 
 */
public class DataPartitionerRemoteSparkMapper extends ParWorker implements PairFlatMapFunction<Tuple2<MatrixIndexes,MatrixBlock>, Long, Writable> 
{
	
	private static final long serialVersionUID = 332887624852010957L;
	
	private long _rlen = -1;
	private long _clen = -1;
	private long _brlen = -1;
	private long _bclen = -1;
	private long _n = -1;
	//private InputInfo _ii = null;
	//private OutputInfo _oi = null;
	private PDataPartitionFormat _dpf = null;
	
	public DataPartitionerRemoteSparkMapper(long rlen, long clen, long brlen, long bclen, InputInfo ii, OutputInfo oi, PDataPartitionFormat dpf) 
		throws DMLRuntimeException, DMLUnsupportedOperationException
	{
		_rlen = rlen;
		_clen = clen;
		_brlen = brlen;
		_bclen = bclen;
		//_ii = ii;
		//_oi = oi;
		_dpf = dpf;
	}


	@Override
	public Iterable<Tuple2<Long, Writable>> call(Tuple2<MatrixIndexes, MatrixBlock> arg0) 
		throws Exception 
	{	
		List<Tuple2<Long, Writable>> ret = new LinkedList<Tuple2<Long, Writable>>();
		
		MatrixIndexes key2 =  arg0._1();
		MatrixBlock value2 = arg0._2();
		long row_offset = (key2.getRowIndex()-1)*_brlen;
		long col_offset = (key2.getColumnIndex()-1)*_bclen;
		
		boolean sparse = value2.isInSparseFormat();
		long nnz = value2.getNonZeros();
		long rows = value2.getNumRows();
		long cols = value2.getNumColumns();
		double sparsity = ((double)nnz)/(rows*cols);
		
		//bound check per block
		if( row_offset + rows < 1 || row_offset + rows > _rlen || col_offset + cols<1 || col_offset + cols > _clen )
		{
			throw new IOException("Matrix block ["+(row_offset+1)+":"+(row_offset+rows)+","+(col_offset+1)+":"+(col_offset+cols)+"] " +
					              "out of overall matrix range [1:"+_rlen+",1:"+_clen+"].");
		}
		
		//partition inputs according to partitioning scheme 
		switch( _dpf )
		{
			case ROW_WISE: {
				for( int i=0; i<rows; i++ ) {
					PairWritableBlock tmp = new PairWritableBlock();
					tmp.indexes = new MatrixIndexes(1, col_offset/_bclen+1);
					tmp.block = new MatrixBlock(1, (int)cols, sparse, (int)(cols*sparsity));
					value2.sliceOperations(i, i, 0, (int)(cols-1), tmp.block);
					ret.add(new Tuple2<Long,Writable>(new Long(row_offset+1+i),tmp));
				}
				break;
			}
			case ROW_BLOCK_WISE: { 
				PairWritableBlock tmp = new PairWritableBlock();
				tmp.indexes = new MatrixIndexes(1, col_offset/_bclen+1);
				tmp.block = new MatrixBlock(value2);
				ret.add(new Tuple2<Long,Writable>(new Long(row_offset/_brlen+1),tmp));
				break;
			}
			case ROW_BLOCK_WISE_N:{ 
				PairWritableBlock tmp = new PairWritableBlock();
				tmp.indexes = new MatrixIndexes(((row_offset%_n)/_brlen)+1, col_offset/_bclen+1);
				tmp.block = new MatrixBlock(value2);
				ret.add(new Tuple2<Long,Writable>(new Long(row_offset/_n+1),tmp));
				break;
			}
			case COLUMN_WISE:{
				for( int i=0; i<cols; i++ ) {
					PairWritableBlock tmp = new PairWritableBlock();
					tmp.indexes = new MatrixIndexes(row_offset/_brlen+1, 1);
					tmp.block = new MatrixBlock(1, (int)cols, sparse, (int)(cols*sparsity));
					value2.sliceOperations(0, (int)(rows-1), i, i, tmp.block);
					ret.add(new Tuple2<Long,Writable>(new Long(col_offset+1+i),tmp));
				}
				break;
			}
			case COLUMN_BLOCK_WISE: {
				PairWritableBlock tmp = new PairWritableBlock();
				tmp.indexes = new MatrixIndexes(row_offset/_brlen+1, 1);
				tmp.block = new MatrixBlock(value2);
				ret.add(new Tuple2<Long,Writable>(new Long(col_offset/_bclen+1),tmp));
				break;
			}
			case COLUMN_BLOCK_WISE_N: {
				PairWritableBlock tmp = new PairWritableBlock();
				tmp.indexes = new MatrixIndexes(row_offset/_brlen+1, ((col_offset%_n)/_bclen)+1);
				tmp.block = new MatrixBlock(value2);
				ret.add(new Tuple2<Long,Writable>(new Long(col_offset/_n+1),tmp));
				break;
			}

			default:
				throw new DMLRuntimeException("Unsupported partition format: "+_dpf);
		}
		
		return ret;
	}
	
}
