package com.outlyer.jmx.jmxquery.model;
/*
 * 	obj.put("value", value);
		obj.put("type", attributeType);
		obj.put("namespace", mBeanName);
		obj.put("metricname", forge(name, attribute, attributeKey));
 * */
public class MetricData {
	private Object value;
	private String nameSpace;
	private String type;
	private String metricName;
	public Object getValue() {
		return value;
	}
	public void setValue(Object value) {
		this.value = value;
	}
	public String getNameSpace() {
		return nameSpace;
	}
	public void setNameSpace(String nameSpace) {
		this.nameSpace = nameSpace;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getMetricName() {
		return metricName;
	}
	public void setMetricName(String metricName) {
		this.metricName = metricName;
	}
	@Override
	public String toString() {
		return "{value=" + value + ", nameSpace=" + nameSpace + ", type=" + type + ", metricName="
				+ metricName + "}";
	}
	
	
}
