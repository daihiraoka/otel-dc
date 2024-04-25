package com.instana.dc.ai.impl.llm;

import com.google.common.collect.ImmutableList;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.resource.v1.Resource;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;


class MetricsCollectorService extends MetricsServiceGrpc.MetricsServiceImplBase {

    public class OtelMetric { 
        private String modelId;
        private long promptTokens;
        private long completeTokens;
        private double duration;
 
        public String getModelId() {
            return modelId;
        }
        public long getPromtTokens() {
            return promptTokens;
        }
        public long getCompleteTokens() {
            return completeTokens;
        }
        public double getDuration() {
            return duration;
        }
        public void setModelId(String modelId) {
            this.modelId = modelId;
        }
        public void setPromptTokens(long promptTokens) {
            this.promptTokens = promptTokens;
        }
        public void setCompleteTokens(long completeTokens) {
            this.completeTokens = completeTokens;
        }
        public void setDuration(double duration) {
            this.duration = duration;
        }
    }

    private final BlockingQueue<OtelMetric> exportMetrics = new LinkedBlockingDeque<>();

    public List<OtelMetric> getMetrics() {
        return ImmutableList.copyOf(exportMetrics);
    }

    public void clearMetrics() {
        exportMetrics.clear();
    }

    @Override
    public void export(
            ExportMetricsServiceRequest request,
            StreamObserver<ExportMetricsServiceResponse> responseObserver) {
 
        System.out.println("--------------------------------------------------------");

        List<ResourceMetrics> allResourceMetrics = request.getResourceMetricsList();
        for (ResourceMetrics resourceMetrics : allResourceMetrics) {

            Resource resource = resourceMetrics.getResource();
            for (KeyValue reskv : resource.getAttributesList()) {
                System.out.println("Received metric resouce --- attrKey: " + reskv.getKey());
                System.out.println("Received metric resouce --- attrVal: " + reskv.getValue().getStringValue());
            }

            for (ScopeMetrics scoMetrics : resourceMetrics.getScopeMetricsList()) {
                InstrumentationScope instrumentationScope = scoMetrics.getScope();
                instrumentationScope.getAttributesList();
                for (KeyValue inskv : instrumentationScope.getAttributesList()) {
                    System.out.println("Received metric scope --- attrKey: " + inskv.getKey());
                    System.out.println("Received metric scope --- attrVal: " + inskv.getValue().getStringValue());
                }

                for (Metric metric : scoMetrics.getMetricsList()) {
                    System.out.println("Received metric --- Name: " + metric.getName());
                    System.out.println("Received metric --- Desc: " + metric.getDescription());
                    System.out.println("Received metric --- Unit: " + metric.getUnit());
                    System.out.println("Received metric --- Case: " + metric.getDataCase().getNumber());

                    switch(metric.getDataCase()) {
                        case SUM:
                            if(metric.getName().compareTo("llm.watsonx.completions.tokens") == 0) {

                                List<NumberDataPoint> sumDataPoints = metric.getSum().getDataPointsList();
                                for (NumberDataPoint dataPoint : sumDataPoints) {

                                    List<KeyValue> kvList = dataPoint.getAttributesList();

                                    String modelId = "";
                                    String tokenType = "";
                                    for (KeyValue kv : kvList) {
                                        System.out.println("Received metric --- Sum attrKey: " + kv.getKey());
                                        System.out.println("Received metric --- Sum attrVal: " + kv.getValue().getStringValue());
                                        if(kv.getKey().compareTo("llm.response.model") == 0) {
                                            modelId = kv.getValue().getStringValue();
                                        } else if(kv.getKey().compareTo("llm.usage.token_type") == 0) {
                                            tokenType = kv.getValue().getStringValue();
                                        }
                                    }

                                    long promptTokens = 0;
                                    long completeTokens = 0;
                                    if (tokenType.compareTo("prompt") == 0) {
                                        promptTokens = dataPoint.getAsInt();
                                        System.out.println("Received metric --- Sum Prompt Value: " + promptTokens);
                                    } else if (tokenType.compareTo("completion") == 0) {
                                        completeTokens = dataPoint.getAsInt();
                                        System.out.println("Received metric --- Sum Complete Value: " + completeTokens);
                                    }

                                    if (!modelId.isEmpty()) {
                                        OtelMetric otelMetric = new OtelMetric();
                                        otelMetric.setModelId(modelId);
                                        otelMetric.setPromptTokens(promptTokens);
                                        otelMetric.setCompleteTokens(completeTokens);
                                        exportMetrics.add(otelMetric);
                                    }
                                }
                            }
                            break;
                        case HISTOGRAM:
                            if(metric.getName().compareTo("llm.watsonx.completions.duration") == 0) {

                                List<HistogramDataPoint> histDataPoints = metric.getHistogram().getDataPointsList();
                                for (HistogramDataPoint dataPoint : histDataPoints) {

                                    List<KeyValue> kvList = dataPoint.getAttributesList();

                                    String modelId = "";
                                    for (KeyValue kv : kvList) {
                                        System.out.println("Received metric --- Histogram attrKey: " + kv.getKey());
                                        System.out.println("Received metric --- Histogram attrVal: " + kv.getValue().getStringValue());
                                        if(kv.getKey().compareTo("llm.response.model") == 0) {
                                            modelId = kv.getValue().getStringValue();
                                        }
                                    }
                                  
                                    Double durationSum = dataPoint.getSum();
                                    System.out.println("Received metric --- Histogram Sum Value: " + durationSum);

                                    if(!modelId.isEmpty()) {
                                        OtelMetric otelMetric = new OtelMetric();
                                        otelMetric.setModelId(modelId);
                                        otelMetric.setDuration(durationSum);
                                        exportMetrics.add(otelMetric);
                                    }
                                }
                            }
                            break;
                        case GAUGE:
                        case SUMMARY:
                        default:
                            System.out.println("Unsupported metric DataCase: " + metric.getDataCase());
                            throw new AssertionError("Unsupported metric DataCase: " + metric.getDataCase());
                    }
                }
            }
        }
        
        responseObserver.onNext(ExportMetricsServiceResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}