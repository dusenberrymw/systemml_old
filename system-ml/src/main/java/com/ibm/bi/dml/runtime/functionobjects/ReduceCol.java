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

package com.ibm.bi.dml.runtime.functionobjects;

import com.ibm.bi.dml.runtime.DMLRuntimeException;
import com.ibm.bi.dml.runtime.matrix.MatrixCharacteristics;
import com.ibm.bi.dml.runtime.matrix.data.MatrixIndexes;
import com.ibm.bi.dml.runtime.matrix.data.MatrixValue.CellIndex;


public class ReduceCol extends IndexFunction
{
	
	private static final long serialVersionUID = 5751707683718583013L;

	private static ReduceCol singleObj = null;
	
	private ReduceCol() {
		// nothing to do here
	}
	
	public static ReduceCol getReduceColFnObject() {
		if ( singleObj == null )
			singleObj = new ReduceCol();
		return singleObj;
	}
	
	public Object clone() throws CloneNotSupportedException {
		// cloning is not supported for singleton classes
		throw new CloneNotSupportedException();
	}
	
	/*
	 * NOTE: index starts from 1 for cells in a matrix, but index starts from 0 for cells inside a block
	 */
	@Override
	public void execute(MatrixIndexes in, MatrixIndexes out) {
		out.setIndexes(in.getRowIndex(), 1);
	}

	@Override
	public void execute(CellIndex in, CellIndex out) {
		out.row=in.row;
		out.column=0;
	}

	@Override
	public boolean computeDimension(int row, int col, CellIndex retDim) {
		retDim.set(row, 1);
		return true;
	}

	public boolean computeDimension(MatrixCharacteristics in, MatrixCharacteristics out) throws DMLRuntimeException
	{
		out.set(in.getRows(), 1, in.getRowsPerBlock(), in.getColsPerBlock());
		return true;
	}
}
