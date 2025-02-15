/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.aggregations.pipeline;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.pipeline.BucketHelpers.GapPolicy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PercentileRanksBucketPipelineAggregator extends BucketMetricsPipelineAggregator {

    private final double[] values;
    private boolean keyed = true;
    private List<Double> data;

    PercentileRanksBucketPipelineAggregator(String name, double[] values, boolean keyed, String[] bucketsPaths,
                                        GapPolicy gapPolicy, DocValueFormat formatter, Map<String, Object> metaData) {
        super(name, bucketsPaths, gapPolicy, formatter, metaData);
        this.values = values;
        this.keyed = keyed;
    }

    /**
     * Read from a stream.
     */
    public PercentileRanksBucketPipelineAggregator(StreamInput in) throws IOException {
        super(in);
        values = in.readDoubleArray();
        keyed = in.readBoolean();
    }

    @Override
    public void innerWriteTo(StreamOutput out) throws IOException {
        out.writeDoubleArray(values);
        out.writeBoolean(keyed);
    }

    @Override
    public String getWriteableName() {
        return PercentileRanksBucketPipelineAggregationBuilder.NAME;
    }

    @Override
    protected void preCollection() {
       data = new ArrayList<>(1024);
    }

    @Override
    protected void collectBucketValue(String bucketKey, Double bucketValue) {
        data.add(bucketValue);
    }

    @Override
    protected InternalAggregation buildAggregation(List<PipelineAggregator> pipelineAggregators, Map<String, Object> metadata) {

        // Perform the sorting and percentile rank collection now that all the data
        // has been collected.
        Collections.sort(data);
        int n = data.size();
        double[] percentileRanks = new double[values.length];

        if (data.size() == 0) {
            for (int i = 0; i < values.length; i++) {
                percentileRanks[i] = Double.NaN;
            }
        } else {
            for (int i = 0; i < values.length; i++) {
                int index = Collections.binarySearch(data, values[i]);
                if (index < 0) {
                    // index < 0 means value is not present in data set. Java returns
                    // -(insertion_point) - 1 as result in this case.
                    // derive insertion_point which shows # of values smaller than value of interest.
                    index = -index-1;
                }
                double percentile_rank = (double) index / n;
                percentileRanks[i] = percentile_rank * 100;
            }
        }

        // todo need postCollection() to clean up temp sorted data?

        return new InternalPercentileRanksBucket(name(), values, PercentileRanks, keyed, format, pipelineAggregators, metadata);
    }
}
