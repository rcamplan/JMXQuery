package com.outlyer.jmx.jmxquery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.util.EC2MetadataUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.outlyer.jmx.jmxquery.model.MetricData;

public class JMXQuryToCloudWatch extends JMXQuery {

	public JMXQuryToCloudWatch() {
		super();
	}

	private static final String DIMENSION_NAME = "InstanceId";

	public static void sendPointToCloudWatchMetrics(String metricName, String standardUnit, Double value, String namespace, String instanceId) {
		final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.defaultClient();
		Dimension dimension = new Dimension().withName(DIMENSION_NAME).withValue(instanceId);
		MetricDatum datum = new MetricDatum().withMetricName(metricName).withUnit(standardUnit).withValue(value)
				.withDimensions(dimension);
		PutMetricDataRequest request = new PutMetricDataRequest().withNamespace(namespace).withMetricData(datum);
		PutMetricDataResult response = cw.putMetricData(request);

	}

	public static void main(String[] args) throws Exception {
		String instanceId = null;
		try {
			instanceId = EC2MetadataUtils.getInstanceId();				
		} catch (Throwable e) {
			System.err.println("ERROR NO INSTANCEID DEFINED");		
		}			
		if(instanceId == null) {
			instanceId = System.getenv("MY_INSTANCE_ID");
			if(instanceId == null) {
				System.err.println("ERROR NO INSTANCEID DEFINED IN ENV");		
				System.exit(255);
			}
		}
		
		JMXQuryToCloudWatch jmx = new JMXQuryToCloudWatch();
		jmx.parse(args);
		jmx.connector = new JMXConnector(jmx.url, jmx.username, jmx.password);
		ArrayList<JMXMetric> values = jmx.connector.getMetrics(jmx.metrics);
		ObjectMapper mapper = new ObjectMapper();
		TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
		HashMap<String, MetricData> result = new HashMap<>();
		for (JMXMetric metric : values) {
			metric.replaceTokens();
			String json = metric.toJSON();
			HashMap<String, Object> mapValue = mapper.readValue(json, typeRef);				
			patchMap(mapValue);
			pushData(mapValue, result);	
		}
		System.out.println(result);
		jmx.connector.disconnect();
		//sendData(instanceId, result);
	}

	private static void sendData(String instanceId2, HashMap<String, MetricData> result) {
		// ClassLoading
		{
			MetricData val = result.get("ClassLoading__TotalLoadedClassCount_");
			Double v = cast(val.getType(), val.getValue());
			sendPointToCloudWatchMetrics(val.getMetricName(), StandardUnit.Count.toString(), v, val.getNameSpace(), instanceId2);
		}
		{
			MetricData val = result.get("ClassLoading__UnloadedClassCount_");
			Double v = cast(val.getType(), val.getValue());
			sendPointToCloudWatchMetrics(val.getMetricName(), StandardUnit.Count.toString(), v, val.getNameSpace(), instanceId2);
		}
		// CodeHeap
		{
			MetricData val = result.get("CodeHeap 'non-nmethods'_type=MemoryPool_Usage_committed");
			Double v = cast(val.getType(), val.getValue());
			sendPointToCloudWatchMetrics(val.getMetricName(), StandardUnit.Bytes.toString(), v, val.getNameSpace(), instanceId2);
		}
		{
			MetricData val = result.get("CodeHeap 'non-profiled nmethods'_type=MemoryPool_Usage_init");
			Double v = cast(val.getType(), val.getValue());
			sendPointToCloudWatchMetrics(val.getMetricName(), StandardUnit.Bytes.toString(), v, val.getNameSpace(), instanceId2);
		}

	}

	private static Double cast(String type, Object value) {
		if(value == null) {
			return null;
		}
		switch (type) {
		case "Long":{
			Long v = coerceToLong(value);
			return v * 1.0;
		}
		case "Integer":{
			Long v = coerceToLong(value);
			return v * 1.0;
		}
		case "Double":{
			Double v = (Double) value;
			return v;
		}
		case "Boolean":{
			Boolean v = (Boolean) value;
			return (v)?1.0:0.0;
		}
			
		default:
			return null;
		}
	}

	private static Long coerceToLong(Object v) {
		if(v instanceof Long) {
			return new Long((Long) v);
		}
		
		if(v instanceof Integer) {
			return new Long((Integer) v);
		}		
		return null;
	}

	private static void pushData(HashMap<String, Object> mapValue, HashMap<String, MetricData> result) {
		String mBeanName = (String) mapValue.get("mBeanName");
		String name = (String) mapValue.get("name");
		String attribute = (String) mapValue.get("attribute");
		String attributeKey = (String) mapValue.getOrDefault("attributeKey", "");
		String attributeType = (String) mapValue.getOrDefault("attributeType", "");
		Object value = mapValue.get("value");
		
		MetricData data = new MetricData();
		data.setType(attributeType);
		data.setMetricName(forge(name, attribute, attributeKey));
		data.setNameSpace(mBeanName);
		data.setValue(value);
		
		result.put(mBeanName + "_" + name + "_" + attribute + "_" + attributeKey, data);

	}

	private static String forge(String name, String attribute, String attributeKey) {
		return ((name == null || name.isEmpty()) ? "" : (name + "_")) + attribute
				+ ((attributeKey == null || attributeKey.isEmpty()) ? "" : (attributeKey + "_")

				);
	}

	private static void patchMap(HashMap<String, Object> mapValue) {
		String mBeanName = ((String) mapValue.get("mBeanName")).replaceAll("java.lang:type=", "")
				.replaceAll("java.lang:name=", "")

		;
		if (mBeanName.contains(",name") || mBeanName.contains(",type")) {
			String[] split = mBeanName.split(",");
			mapValue.put("mBeanName", split[0]);
			mapValue.put("name", split[1].replaceAll("name=", "").replaceAll("type=", ""));
		} else {
			mapValue.put("mBeanName", mBeanName);
			mapValue.put("name", "");
		}
	}
}
