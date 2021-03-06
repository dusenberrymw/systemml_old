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

package com.ibm.bi.dml.runtime.transform;
import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;

import com.ibm.bi.dml.runtime.DMLRuntimeException;
import com.ibm.bi.dml.runtime.matrix.mapred.MRJobConfiguration;
import org.apache.wink.json4j.JSONObject;

public class ApplyTfCSVMapper implements Mapper<LongWritable, Text, NullWritable, Text> {
	
	
	ApplyTfHelper tfmapper = null;
	Reporter _reporter = null;
	
	@Override
	public void configure(JobConf job) {
		try {
			tfmapper = new ApplyTfHelper(job);
			JSONObject spec = tfmapper.parseSpec();
			tfmapper.setupTfAgents(spec);
			tfmapper.loadTfMetadata(spec);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void map(LongWritable rawKey, Text rawValue, OutputCollector<NullWritable, Text> out, Reporter reporter) throws IOException  {
		
		// output the header line
		if ( rawKey.get() == 0 && tfmapper._partFileWithHeader ) 
		{
			_reporter = reporter;
			tfmapper.processHeaderLine(rawValue);
			if ( tfmapper._hasHeader )
				return;
		}
		
		// parse the input line and apply transformation
		String[] words = tfmapper.getWords(rawValue);
		if(!tfmapper.omit(words))
		{
			try {
				words = tfmapper.apply(words);
				String outStr = tfmapper.checkAndPrepOutputString(words, tfmapper._da);
				out.collect(NullWritable.get(), new Text(outStr));
			} catch(DMLRuntimeException e)
			{
				throw new RuntimeException(e.getMessage() + ": " + rawValue.toString());
			}
		}
	}

	@Override
	public void close() throws IOException {
		_reporter.incrCounter(MRJobConfiguration.DataTransformCounters.TRANSFORMED_NUM_ROWS, tfmapper.getNumTransformedRows());
		_reporter.incrCounter(MRJobConfiguration.DataTransformCounters.TRANSFORMED_NUM_COLS, tfmapper.getNumTransformedColumns());
	}

}
