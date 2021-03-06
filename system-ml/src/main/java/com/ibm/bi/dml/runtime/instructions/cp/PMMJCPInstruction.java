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

package com.ibm.bi.dml.runtime.instructions.cp;

import com.ibm.bi.dml.parser.Expression.DataType;
import com.ibm.bi.dml.parser.Expression.ValueType;
import com.ibm.bi.dml.runtime.DMLRuntimeException;
import com.ibm.bi.dml.runtime.DMLUnsupportedOperationException;
import com.ibm.bi.dml.runtime.controlprogram.context.ExecutionContext;
import com.ibm.bi.dml.runtime.instructions.Instruction;
import com.ibm.bi.dml.runtime.instructions.InstructionUtils;
import com.ibm.bi.dml.runtime.matrix.data.MatrixBlock;
import com.ibm.bi.dml.runtime.matrix.operators.Operator;

/**
 * 
 * 
 */
public class PMMJCPInstruction extends ComputationCPInstruction
{	
	
	private int _numThreads = -1;
	
	public PMMJCPInstruction(Operator op, CPOperand in1, CPOperand in2, CPOperand in3, CPOperand out, int k, String opcode, String istr)
	{
		super(op, in1, in2, in3, out, opcode, istr);
		_numThreads = k;
	}
	
	/**
	 * 
	 * @param str
	 * @return
	 * @throws DMLRuntimeException
	 */
	public static Instruction parseInstruction ( String str ) 
		throws DMLRuntimeException 
	{
		CPOperand in1 = new CPOperand("", ValueType.UNKNOWN, DataType.UNKNOWN);
		CPOperand in2 = new CPOperand("", ValueType.UNKNOWN, DataType.UNKNOWN);
		CPOperand in3 = new CPOperand("", ValueType.UNKNOWN, DataType.UNKNOWN);
		CPOperand out = new CPOperand("", ValueType.UNKNOWN, DataType.UNKNOWN);
		
		InstructionUtils.checkNumFields ( str, 5 );
		
		String[] parts = InstructionUtils.getInstructionPartsWithValueType(str);
		String opcode = parts[0];
		in1.split(parts[1]);
		in2.split(parts[2]);
		in3.split(parts[3]);
		out.split(parts[4]);
		int k = Integer.parseInt(parts[5]);
		
		if(!opcode.equalsIgnoreCase("pmm"))
			throw new DMLRuntimeException("Unknown opcode while parsing an PMMJCPInstruction: " + str);
		else
			return new PMMJCPInstruction(new Operator(true), in1, in2, in3, out, k, opcode, str);
	}
	
	@Override
	public void processInstruction(ExecutionContext ec)
		throws DMLUnsupportedOperationException, DMLRuntimeException 
	{
		//get inputs
		MatrixBlock matBlock1 = ec.getMatrixInput(input1.getName());
		MatrixBlock matBlock2 = ec.getMatrixInput(input2.getName());
		int rlen = (int)ec.getScalarInput(input3.getName(), input3.getValueType(), input3.isLiteral()).getLongValue();
		
		//execute operations
		MatrixBlock ret = new MatrixBlock(rlen, matBlock2.getNumColumns(), matBlock2.isInSparseFormat());
		matBlock1.permutationMatrixMultOperations(matBlock2, ret, null, _numThreads);
		
		//set output and release inputs
		ec.setMatrixOutput(output.getName(), ret);
		ec.releaseMatrixInput(input1.getName());
		ec.releaseMatrixInput(input2.getName());
	}
}
