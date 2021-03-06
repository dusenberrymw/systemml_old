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

package com.ibm.bi.dml.runtime.instructions.spark.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFlatMapFunction;
import org.apache.spark.api.java.function.PairFunction;

import scala.Tuple2;

import com.ibm.bi.dml.runtime.controlprogram.parfor.stat.InfrastructureAnalyzer;
import com.ibm.bi.dml.runtime.instructions.spark.functions.ReplicateVectorFunction;
import com.ibm.bi.dml.runtime.matrix.data.MatrixBlock;
import com.ibm.bi.dml.runtime.matrix.data.MatrixIndexes;
import com.ibm.bi.dml.runtime.util.DataConverter;
import com.ibm.bi.dml.runtime.util.UtilFunctions;

/**
 * 
 */
public class RDDSortUtils 
{
	
	/**
	 * 
	 * @param in
	 * @param rlen
	 * @param brlen
	 * @return
	 */
	public static JavaPairRDD<MatrixIndexes, MatrixBlock> sortByVal( JavaPairRDD<MatrixIndexes, MatrixBlock> in, long rlen, int brlen )
	{
		//create value-index rdd from inputs
		JavaRDD<Double> dvals = in.values()
				.flatMap(new ExtractDoubleValuesFunction());
	
		//sort (creates sorted range per partition)
		long hdfsBlocksize = InfrastructureAnalyzer.getHDFSBlockSize();
		int numPartitions = (int)Math.ceil(((double)rlen*8)/hdfsBlocksize);
		JavaRDD<Double> sdvals = dvals
				.sortBy(new CreateDoubleKeyFunction(), true, numPartitions);
		
		//create binary block output
		JavaPairRDD<MatrixIndexes, MatrixBlock> ret = sdvals
				.zipWithIndex()
		        .mapPartitions(new ConvertToBinaryBlockFunction(rlen, brlen))
		        .mapToPair(new UnfoldBinaryBlockFunction());
		ret = RDDAggregateUtils.mergeByKey(ret);	
		
		return ret;
	}
	
	/**
	 * 
	 * @param in
	 * @param in2
	 * @param rlen
	 * @param brlen
	 * @return
	 */
	public static JavaPairRDD<MatrixIndexes, MatrixBlock> sortByVal( JavaPairRDD<MatrixIndexes, MatrixBlock> in, 
			JavaPairRDD<MatrixIndexes, MatrixBlock> in2, long rlen, int brlen )
	{
		//create value-index rdd from inputs
		JavaRDD<DoublePair> dvals = in.join(in2).values()
				.flatMap(new ExtractDoubleValuesFunction2());
	
		//sort (creates sorted range per partition)
		long hdfsBlocksize = InfrastructureAnalyzer.getHDFSBlockSize();
		int numPartitions = (int)Math.ceil(((double)rlen*8)/hdfsBlocksize);
		JavaRDD<DoublePair> sdvals = dvals
				.sortBy(new CreateDoubleKeyFunction2(), true, numPartitions);

		//create binary block output
		JavaPairRDD<MatrixIndexes, MatrixBlock> ret = sdvals
				.zipWithIndex()
		        .mapPartitions(new ConvertToBinaryBlockFunction2(rlen, brlen))
		        .mapToPair(new UnfoldBinaryBlockFunction());
		ret = RDDAggregateUtils.mergeByKey(ret);		
		
		return ret;
	}
	
	/**
	 * 
	 * @param in
	 * @param rlen
	 * @param brlen
	 * @return
	 */
	public static JavaPairRDD<MatrixIndexes, MatrixBlock> sortIndexesByVal( JavaPairRDD<MatrixIndexes, MatrixBlock> val, 
			boolean asc, long rlen, int brlen )
	{
		//create value-index rdd from inputs
		JavaPairRDD<ValueIndexPair, Double> dvals = val
				.flatMapToPair(new ExtractDoubleValuesWithIndexFunction(brlen));
	
		//sort (creates sorted range per partition)
		long hdfsBlocksize = InfrastructureAnalyzer.getHDFSBlockSize();
		int numPartitions = (int)Math.ceil(((double)rlen*16)/hdfsBlocksize);
		JavaRDD<ValueIndexPair> sdvals = dvals
				.sortByKey(new IndexComparator(asc), true, numPartitions)
				.keys(); //workaround for index comparator
	 
		//create binary block output
		JavaPairRDD<MatrixIndexes, MatrixBlock> ret = sdvals
				.zipWithIndex()
		        .mapPartitions(new ConvertToBinaryBlockFunction3(rlen, brlen))
		        .mapToPair(new UnfoldBinaryBlockFunction());
		ret = RDDAggregateUtils.mergeByKey(ret);		
		
		return ret;	
	}
	
	/**
	 * 
	 * @param val
	 * @param data
	 * @param asc
	 * @param rlen
	 * @param brlen
	 * @return
	 */
	public static JavaPairRDD<MatrixIndexes, MatrixBlock> sortDataByVal( JavaPairRDD<MatrixIndexes, MatrixBlock> val, 
			JavaPairRDD<MatrixIndexes, MatrixBlock> data, boolean asc, long rlen, long clen, int brlen, int bclen )
	{
		//create value-index rdd from inputs
		JavaPairRDD<ValueIndexPair, Double> dvals = val
				.flatMapToPair(new ExtractDoubleValuesWithIndexFunction(brlen));
	
		//sort (creates sorted range per partition)
		long hdfsBlocksize = InfrastructureAnalyzer.getHDFSBlockSize();
		int numPartitions = (int)Math.ceil(((double)rlen*16)/hdfsBlocksize);
		JavaRDD<ValueIndexPair> sdvals = dvals
				.sortByKey(new IndexComparator(asc), true, numPartitions)
				.keys(); //workaround for index comparator
	 
		//create target indexes by original index
		long numRep = (long)Math.ceil((double)clen/bclen);
		JavaPairRDD<MatrixIndexes, MatrixBlock> ixmap = sdvals
				.zipWithIndex()
				.mapToPair(new ExtractIndexFunction())
				.sortByKey()
		        .mapPartitions(new ConvertToBinaryBlockFunction4(rlen, brlen))
		        .mapToPair(new UnfoldBinaryBlockFunction());
		ixmap = RDDAggregateUtils.mergeByKey(ixmap);		
		
		//replicate indexes for all column blocks
		JavaPairRDD<MatrixIndexes, MatrixBlock> rixmap = ixmap
				.flatMapToPair(new ReplicateVectorFunction(false, numRep));      
		
		//create binary block output
		JavaPairRDD<MatrixIndexes, MatrixBlock> ret = data
				.join(rixmap)
				.flatMapToPair(new ShuffleMatrixBlockRowsFunction(rlen, brlen));
		ret = RDDAggregateUtils.mergeByKey(ret);
		
		return ret;	
	}
	
	/**
	 * 
	 */
	private static class ExtractDoubleValuesFunction implements FlatMapFunction<MatrixBlock,Double> 
	{
		private static final long serialVersionUID = 6888003502286282876L;

		@Override
		public Iterable<Double> call(MatrixBlock arg0) 
			throws Exception 
		{
			return DataConverter.convertToDoubleList(arg0);
		}		
	}

	/**
	 * 
	 */
	private static class ExtractDoubleValuesFunction2 implements FlatMapFunction<Tuple2<MatrixBlock,MatrixBlock>,DoublePair> 
	{
		private static final long serialVersionUID = 2132672563825289022L;

		@Override
		public Iterable<DoublePair> call(Tuple2<MatrixBlock,MatrixBlock> arg0) 
			throws Exception 
		{
			ArrayList<DoublePair> ret = new ArrayList<DoublePair>(); 
			MatrixBlock mb1 = arg0._1();
			MatrixBlock mb2 = arg0._2();
			
			for( int i=0; i<mb1.getNumRows(); i++) {
				ret.add(new DoublePair(
						mb1.quickGetValue(i, 0),
						mb2.quickGetValue(i, 0)));
			}
			
			return ret;
		}		
	}
	
	private static class ExtractDoubleValuesWithIndexFunction implements PairFlatMapFunction<Tuple2<MatrixIndexes,MatrixBlock>,ValueIndexPair,Double> 
	{
		private static final long serialVersionUID = -3976735381580482118L;
		
		private int _brlen = -1;
		
		public ExtractDoubleValuesWithIndexFunction(int brlen)
		{
			_brlen = brlen;
		}
		
		@Override
		public Iterable<Tuple2<ValueIndexPair,Double>> call(Tuple2<MatrixIndexes,MatrixBlock> arg0) 
			throws Exception 
		{
			ArrayList<Tuple2<ValueIndexPair,Double>> ret = new ArrayList<Tuple2<ValueIndexPair,Double>>(); 
			MatrixIndexes ix = arg0._1();
			MatrixBlock mb = arg0._2();
			
			long ixoffset = (ix.getRowIndex()-1)*_brlen;
			for( int i=0; i<mb.getNumRows(); i++) {
				double val = mb.quickGetValue(i, 0);
				ret.add(new Tuple2<ValueIndexPair,Double>(
						new ValueIndexPair(val,ixoffset+i+1), val));
			}
			
			return ret;
		}		
	}
	
	/**
	 * 
	 */
	private static class CreateDoubleKeyFunction implements Function<Double,Double> 
	{
		private static final long serialVersionUID = 2021786334763247835L;

		@Override
		public Double call(Double arg0) 
			throws Exception 
		{
			return arg0;
		}		
	}
	
	/**
	 * 
	 */
	private static class CreateDoubleKeyFunction2 implements Function<DoublePair,Double> 
	{
		private static final long serialVersionUID = -7954819651274239592L;

		@Override
		public Double call(DoublePair arg0) 
			throws Exception 
		{
			return arg0.val1;
		}		
	}
	
	/**
	 * 
	 */
	private static class ExtractIndexFunction implements PairFunction<Tuple2<ValueIndexPair,Long>,Long,Long> 
	{
		private static final long serialVersionUID = -4553468724131249535L;

		@Override
		public Tuple2<Long, Long> call(Tuple2<ValueIndexPair,Long> arg0)
				throws Exception 
		{
			return new Tuple2<Long,Long>(arg0._1().ix, arg0._2());
		}

	}
	
	/**
	 * 
	 */
	private static class ConvertToBinaryBlockFunction implements FlatMapFunction<Iterator<Tuple2<Double,Long>>,Tuple2<MatrixIndexes,MatrixBlock>> 
	{
		private static final long serialVersionUID = 5000298196472931653L;
		
		private long _rlen = -1;
		private int _brlen = -1;
		
		public ConvertToBinaryBlockFunction(long rlen, int brlen)
		{
			_rlen = rlen;
			_brlen = brlen;
		}
		
		public Iterable<Tuple2<MatrixIndexes, MatrixBlock>> call(Iterator<Tuple2<Double,Long>> arg0) 
			throws Exception 
		{
			ArrayList<Tuple2<MatrixIndexes,MatrixBlock>> ret = new ArrayList<Tuple2<MatrixIndexes,MatrixBlock>>();
			
			MatrixIndexes ix = null;
			MatrixBlock mb = null;
			
			while( arg0.hasNext() ) 
			{
				Tuple2<Double,Long> val = arg0.next();
				long valix = val._2 + 1;
				long rix = UtilFunctions.blockIndexCalculation(valix, _brlen);
				int pos = UtilFunctions.cellInBlockCalculation(valix, _brlen);
				
				if( ix == null || ix.getRowIndex() != rix )
				{
					if( ix !=null )
						ret.add(new Tuple2<MatrixIndexes,MatrixBlock>(ix,mb));
					long len = UtilFunctions.computeBlockSize(_rlen, rix, _brlen);
					ix = new MatrixIndexes(rix,1);
					mb = new MatrixBlock((int)len, 1, false);	
				}
				
				mb.quickSetValue(pos, 0, val._1);
			}
			
			//flush last block
			if( mb!=null && mb.getNonZeros() != 0 )
				ret.add(new Tuple2<MatrixIndexes,MatrixBlock>(ix,mb));
			
			return ret;
		}
	}

	/**
	 * 
	 */
	private static class ConvertToBinaryBlockFunction2 implements FlatMapFunction<Iterator<Tuple2<DoublePair,Long>>,Tuple2<MatrixIndexes,MatrixBlock>> 
	{
		private static final long serialVersionUID = -8638434373377180192L;
		
		private long _rlen = -1;
		private int _brlen = -1;
		
		public ConvertToBinaryBlockFunction2(long rlen, int brlen)
		{
			_rlen = rlen;
			_brlen = brlen;
		}
		
		public Iterable<Tuple2<MatrixIndexes, MatrixBlock>> call(Iterator<Tuple2<DoublePair,Long>> arg0) 
			throws Exception
		{
			ArrayList<Tuple2<MatrixIndexes,MatrixBlock>> ret = new ArrayList<Tuple2<MatrixIndexes,MatrixBlock>>();
			
			MatrixIndexes ix = null;
			MatrixBlock mb = null;
			
			while( arg0.hasNext() ) 
			{
				Tuple2<DoublePair,Long> val = arg0.next();
				long valix = val._2 + 1;
				long rix = UtilFunctions.blockIndexCalculation(valix, _brlen);
				int pos = UtilFunctions.cellInBlockCalculation(valix, _brlen);
				
				if( ix == null || ix.getRowIndex() != rix )
				{
					if( ix !=null )
						ret.add(new Tuple2<MatrixIndexes,MatrixBlock>(ix,mb));
					long len = UtilFunctions.computeBlockSize(_rlen, rix, _brlen);
					ix = new MatrixIndexes(rix,1);
					mb = new MatrixBlock((int)len, 2, false);	
				}
				
				mb.quickSetValue(pos, 0, val._1.val1);
				mb.quickSetValue(pos, 1, val._1.val2);
			}
			
			//flush last block
			if( mb!=null && mb.getNonZeros() != 0 )
				ret.add(new Tuple2<MatrixIndexes,MatrixBlock>(ix,mb));
			
			return ret;
		}
	}
	
	/**
	 * 
	 */
	private static class ConvertToBinaryBlockFunction3 implements FlatMapFunction<Iterator<Tuple2<ValueIndexPair,Long>>,Tuple2<MatrixIndexes,MatrixBlock>> 
	{		
		private static final long serialVersionUID = 9113122668214965797L;
		
		private long _rlen = -1;
		private int _brlen = -1;
		
		public ConvertToBinaryBlockFunction3(long rlen, int brlen)
		{
			_rlen = rlen;
			_brlen = brlen;
		}
		
		public Iterable<Tuple2<MatrixIndexes, MatrixBlock>> call(Iterator<Tuple2<ValueIndexPair,Long>> arg0) 
			throws Exception
		{
			ArrayList<Tuple2<MatrixIndexes,MatrixBlock>> ret = new ArrayList<Tuple2<MatrixIndexes,MatrixBlock>>();
			
			MatrixIndexes ix = null;
			MatrixBlock mb = null;
			
			while( arg0.hasNext() ) 
			{
				Tuple2<ValueIndexPair,Long> val = arg0.next();
				long valix = val._2 + 1;
				long rix = UtilFunctions.blockIndexCalculation(valix, _brlen);
				int pos = UtilFunctions.cellInBlockCalculation(valix, _brlen);
				
				if( ix == null || ix.getRowIndex() != rix )
				{
					if( ix !=null )
						ret.add(new Tuple2<MatrixIndexes,MatrixBlock>(ix,mb));
					long len = UtilFunctions.computeBlockSize(_rlen, rix, _brlen);
					ix = new MatrixIndexes(rix,1);
					mb = new MatrixBlock((int)len, 1, false);	
				}
				
				mb.quickSetValue(pos, 0, val._1.ix);
			}
			
			//flush last block
			if( mb!=null && mb.getNonZeros() != 0 )
				ret.add(new Tuple2<MatrixIndexes,MatrixBlock>(ix,mb));
			
			return ret;
		}
	}
	
	/**
	 * 
	 */
	private static class ConvertToBinaryBlockFunction4 implements FlatMapFunction<Iterator<Tuple2<Long,Long>>,Tuple2<MatrixIndexes,MatrixBlock>> 
	{	
		private static final long serialVersionUID = 9113122668214965797L;
		
		private long _rlen = -1;
		private int _brlen = -1;
		
		public ConvertToBinaryBlockFunction4(long rlen, int brlen)
		{
			_rlen = rlen;
			_brlen = brlen;
		}
		
		public Iterable<Tuple2<MatrixIndexes, MatrixBlock>> call(Iterator<Tuple2<Long,Long>> arg0) 
			throws Exception
		{
			ArrayList<Tuple2<MatrixIndexes,MatrixBlock>> ret = new ArrayList<Tuple2<MatrixIndexes,MatrixBlock>>();
			
			MatrixIndexes ix = null;
			MatrixBlock mb = null;
			
			while( arg0.hasNext() ) 
			{
				Tuple2<Long,Long> val = arg0.next();
				long valix = val._1;
				long rix = UtilFunctions.blockIndexCalculation(valix, _brlen);
				int pos = UtilFunctions.cellInBlockCalculation(valix, _brlen);
				
				if( ix == null || ix.getRowIndex() != rix )
				{
					if( ix !=null )
						ret.add(new Tuple2<MatrixIndexes,MatrixBlock>(ix,mb));
					long len = UtilFunctions.computeBlockSize(_rlen, rix, _brlen);
					ix = new MatrixIndexes(rix,1);
					mb = new MatrixBlock((int)len, 1, false);	
				}
				
				mb.quickSetValue(pos, 0, val._2+1);
			}
			
			//flush last block
			if( mb!=null && mb.getNonZeros() != 0 )
				ret.add(new Tuple2<MatrixIndexes,MatrixBlock>(ix,mb));
			
			return ret;
		}
	}
	
	private static class ShuffleMatrixBlockRowsFunction implements PairFlatMapFunction<Tuple2<MatrixIndexes,Tuple2<MatrixBlock,MatrixBlock>>,MatrixIndexes,MatrixBlock> 
	{	
		private static final long serialVersionUID = 6885207719329119646L;
		
		private long _rlen = -1;
		private int _brlen = -1;
		
		public ShuffleMatrixBlockRowsFunction(long rlen, int brlen)
		{
			_rlen = rlen;
			_brlen = brlen;
		}

		@Override
		public Iterable<Tuple2<MatrixIndexes, MatrixBlock>> call(Tuple2<MatrixIndexes, Tuple2<MatrixBlock, MatrixBlock>> arg0)
			throws Exception 
		{
			MatrixBlock data = arg0._2()._1();
			MatrixBlock ixmap = arg0._2()._2();
			
			ArrayList<Tuple2<MatrixIndexes, MatrixBlock>> ret = new ArrayList<Tuple2<MatrixIndexes,MatrixBlock>>();			
			for( int i=0; i<data.getNumRows(); i++) 
			{
				long valix = (long) ixmap.quickGetValue(i, 0);
				long rix = UtilFunctions.blockIndexCalculation(valix, _brlen);
				int pos = UtilFunctions.cellInBlockCalculation(valix, _brlen);
				long len = UtilFunctions.computeBlockSize(_rlen, rix, _brlen);
				MatrixIndexes lix = new MatrixIndexes(rix,1);
				MatrixBlock lmb = new MatrixBlock((int)len, data.getNumColumns(), true);	
				MatrixBlock tmp = data.sliceOperations(i, i, 0, data.getNumColumns()-1, new MatrixBlock());
				lmb.leftIndexingOperations(tmp, pos, pos, 0, data.getNumColumns()-1, lmb, true);
				ret.add(new Tuple2<MatrixIndexes,MatrixBlock>(lix, lmb));
			}			

			return ret;
		}
	}
	
	/**
	 * 
	 */
	private static class UnfoldBinaryBlockFunction implements PairFunction<Tuple2<MatrixIndexes,MatrixBlock>,MatrixIndexes,MatrixBlock> 
	{
		private static final long serialVersionUID = -5509821097041916225L;

		@Override
		public Tuple2<MatrixIndexes, MatrixBlock> call(Tuple2<MatrixIndexes, MatrixBlock> t) 
			throws Exception 
		{
			return t;
		}
	}
	
	/**
	 * More memory-efficient representation than Tuple2<Double,Double> which requires
	 * three instead of one object per cell.
	 */
	private static class DoublePair implements Serializable
	{
		private static final long serialVersionUID = 4373356163734559009L;
		
		public double val1;
		public double val2;
		
		public DoublePair(double d1, double d2) {
			val1 = d1;
			val2 = d2;
		}
	}
	
	/**
	 * 
	 */
	private static class ValueIndexPair implements Serializable 
	{
		private static final long serialVersionUID = -3273385845538526829L;
		
		public double val; 
		public long ix; 

		public ValueIndexPair(double dval, long lix) {
			val = dval;
			ix = lix;
		}
	}
	
	public static class IndexComparator implements Comparator<ValueIndexPair>, Serializable 
	{
		private static final long serialVersionUID = 5154839870549241343L;
		
		private boolean _asc;
		public IndexComparator(boolean asc) {
			_asc = asc;
		}
			
		@Override
		public int compare(ValueIndexPair o1, ValueIndexPair o2) 
		{
			//note: use conversion to Double and Long instead of native
			//compare for compatibility with jdk 6
			int retVal = Double.valueOf(o1.val).compareTo(o2.val);
			if(retVal != 0) {
				return (_asc ? retVal : -1*retVal);
			}
			else {
				//for stable sort
				return Long.valueOf(o1.ix).compareTo(o2.ix);
			}
		}
		
	}
}
