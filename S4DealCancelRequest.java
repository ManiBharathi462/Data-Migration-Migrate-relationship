package com.hpe.s4.integration.bean;

public class S4DealCancelRequest {
	 
	private String dealIdentifier;
	private String extquotenr;
	private String dealwonlostcd;
	
	public String getDealIdentifier() {
		return dealIdentifier;
	}
	public void setDealIdentifier(String dealIdentifier) {
		this.dealIdentifier = dealIdentifier;
	}
	public String getExtquotenr() {
		return extquotenr;
	}
	public void setExtquotenr(String extquotenr) {
		this.extquotenr = extquotenr;
	}
	public String getDealwonlostcd() {
		return dealwonlostcd;
	}
	public void setDealwonlostcd(String dealwonlostcd) {
		this.dealwonlostcd = dealwonlostcd;
	}
		
}
