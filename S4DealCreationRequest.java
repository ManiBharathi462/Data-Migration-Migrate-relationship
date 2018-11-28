package com.hpe.s4.deal.beans;

import java.util.Date;
import java.util.List;

import javax.xml.datatype.XMLGregorianCalendar;

public class S4DealCreationRequest {
	
	private List<DealProductDetail> DealProductDetails;
	private List<DealDetail> DealDetails;
	private List<ResellerDetail> ResellerADetails;
	private List<ResellerDetail> ResellerBDetails;
//	private List<CreateDealResultSet> ETY_OM_CREATE_DEAL_RESSet;
	private String CatalogIndicator;
	private String DealSourceSystemIdentifier;
	private String CurrencyCode;
	private String SourceDealVersionNumber;
	private String ValidEndDate;
	private String DealRegistrationIdentifier;
	private String ExternalQuoteDisplayIndicator;
	private String CustomerEngagementTypeName;
	private String PaymentTermCode;
	private String DealDescription;
	private String ConfigToOrderIndicator;
	private String ConflictCheckIndicator;
	private String SourceDealIdentifier;
	private String SalesForceCaseNumber;
	private String UserIdentifier;
	private String RoutingIndicator;
	private String PriceListTypeCode;
	private String AutomatedSalesApprovalProcessIndicat;
	private String ValueVolumeOptionCode;
	private Date CommentDate;
	private String CustomerPartyIdentifier;
	private String DealTypeCode;
	private String PriceTermCode;
	private String DiscountTypeCode;
	private String EmployeeEmailIdentifier;
	private String DealCreatorCode;
	private String CorporateResellerIndicator;
	private String ReceiveQuoteOutputIndicator;
	private String OtherPartySiteInstanceIdentifier;
	private String CustomerSegmentCode;
	private String LeadCountryCode;
	private String SalesOpportunityIdentifier;
	private String DealUserTypeCode;
	private String OtherPartySiteIdentifier;
	private String ValidStartDate;
	private String IndustryCode;
	private String ProductMixCode;
	private String BusinessRelationshipTypeCode;
	private String OrganizationIdentifier;
	private String DealSourceSystemCode;
	private String DealStatusName;
	private String CustomerLatinName;
	private String MiscellaneousChargeCode;
	private String DealVersionNumber;
	private String RequestType;
	private String SourceDealTypeCode;
	private String CustomerSatisfactionIndicator;
	private String CommentType;
	private String EmployeeIdentifier;
	private String LeadBusinessUnitCode;
	private String RequestIdentifier;
	private String PriceProtectionIndicator;
	private String FrameworkIndicator;
	private String Comments;
	private String NonReusableIndicator;
	private String CommentSetting;
	private String LeadBusinessAreaGroupCode;
	private String ComplexIndicator;
	private String RouteToMarketTypeCode;
	private String ExclusivePartnerIndicator;
	private String LineaddedbyEmployeeNumber;
	private String TenantID;
	private String SFDCOppID;
	public String getCatalogIndicator() {
		return CatalogIndicator;
	}
	public void setCatalogIndicator(String yesNoFlagType) {
		CatalogIndicator = yesNoFlagType;
	}
	public String getDealSourceSystemIdentifier() {
		return DealSourceSystemIdentifier;
	}
	public void setDealSourceSystemIdentifier(String dealSourceSystemIdentifier) {
		DealSourceSystemIdentifier = dealSourceSystemIdentifier;
	}
	public String getCurrencyCode() {
		return CurrencyCode;
	}
	public void setCurrencyCode(String currencyCode) {
		CurrencyCode = currencyCode;
	}
	public String getSourceDealVersionNumber() {
		return SourceDealVersionNumber;
	}
	public void setSourceDealVersionNumber(String sourceDealVersionNumber) {
		SourceDealVersionNumber = sourceDealVersionNumber;
	}
	public String getValidEndDate() {
		return ValidEndDate;
	}
	public void setValidEndDate(String xmlGregorianCalendar) {
		ValidEndDate = xmlGregorianCalendar;
	}
	public String getDealRegistrationIdentifier() {
		return DealRegistrationIdentifier;
	}
	public void setDealRegistrationIdentifier(String yesNoFlagType) {
		DealRegistrationIdentifier = yesNoFlagType;
	}
	public String getExternalQuoteDisplayIndicator() {
		return ExternalQuoteDisplayIndicator;
	}
	public void setExternalQuoteDisplayIndicator(String externalQuoteDisplayIndicator) {
		ExternalQuoteDisplayIndicator = externalQuoteDisplayIndicator;
	}
	public String getCustomerEngagementTypeName() {
		return CustomerEngagementTypeName;
	}
	public void setCustomerEngagementTypeName(String customerEngagementTypeName) {
		CustomerEngagementTypeName = customerEngagementTypeName;
	}
	public String getPaymentTermCode() {
		return PaymentTermCode;
	}
	public void setPaymentTermCode(String paymentTermCode) {
		PaymentTermCode = paymentTermCode;
	}
	public String getDealDescription() {
		return DealDescription;
	}
	public void setDealDescription(String dealDescription) {
		DealDescription = dealDescription;
	}
	public String getConfigToOrderIndicator() {
		return ConfigToOrderIndicator;
	}
	public void setConfigToOrderIndicator(String configToOrderIndicator) {
		ConfigToOrderIndicator = configToOrderIndicator;
	}
	public String getConflictCheckIndicator() {
		return ConflictCheckIndicator;
	}
	public void setConflictCheckIndicator(String yesNoFlagType) {
		ConflictCheckIndicator = yesNoFlagType;
	}
	public String getSourceDealIdentifier() {
		return SourceDealIdentifier;
	}
	public void setSourceDealIdentifier(String sourceDealIdentifier) {
		SourceDealIdentifier = sourceDealIdentifier;
	}
	public String getSalesForceCaseNumber() {
		return SalesForceCaseNumber;
	}
	public void setSalesForceCaseNumber(String salesForceCaseNumber) {
		SalesForceCaseNumber = salesForceCaseNumber;
	}
	public String getUserIdentifier() {
		return UserIdentifier;
	}
	public void setUserIdentifier(String userIdentifier) {
		UserIdentifier = userIdentifier;
	}
	public String getRoutingIndicator() {
		return RoutingIndicator;
	}
	public void setRoutingIndicator(String routingIndicator) {
		RoutingIndicator = routingIndicator;
	}
	public String getPriceListTypeCode() {
		return PriceListTypeCode;
	}
	public void setPriceListTypeCode(String priceListTypeCode) {
		PriceListTypeCode = priceListTypeCode;
	}
	public String getAutomatedSalesApprovalProcessIndicat() {
		return AutomatedSalesApprovalProcessIndicat;
	}
	public void setAutomatedSalesApprovalProcessIndicat(String automatedSalesApprovalProcessIndicat) {
		AutomatedSalesApprovalProcessIndicat = automatedSalesApprovalProcessIndicat;
	}
	public String getValueVolumeOptionCode() {
		return ValueVolumeOptionCode;
	}
	public void setValueVolumeOptionCode(String valueVolumeOptionCode) {
		ValueVolumeOptionCode = valueVolumeOptionCode;
	}
	public Date getCommentDate() {
		return CommentDate;
	}
	public void setCommentDate(Date commentDate) {
		CommentDate = commentDate;
	}
	public String getCustomerPartyIdentifier() {
		return CustomerPartyIdentifier;
	}
	public void setCustomerPartyIdentifier(String customerPartyIdentifier) {
		CustomerPartyIdentifier = customerPartyIdentifier;
	}
	public String getDealTypeCode() {
		return DealTypeCode;
	}
	public void setDealTypeCode(String dealTypeCode) {
		DealTypeCode = dealTypeCode;
	}
	public String getPriceTermCode() {
		return PriceTermCode;
	}
	public void setPriceTermCode(String priceTermCode) {
		PriceTermCode = priceTermCode;
	}
	public String getDiscountTypeCode() {
		return DiscountTypeCode;
	}
	public void setDiscountTypeCode(String discountTypeCode) {
		DiscountTypeCode = discountTypeCode;
	}
	public String getEmployeeEmailIdentifier() {
		return EmployeeEmailIdentifier;
	}
	public void setEmployeeEmailIdentifier(String employeeEmailIdentifier) {
		EmployeeEmailIdentifier = employeeEmailIdentifier;
	}
	public String getDealCreatorCode() {
		return DealCreatorCode;
	}
	public void setDealCreatorCode(String dealCreatorCode) {
		DealCreatorCode = dealCreatorCode;
	}
	public String getCorporateResellerIndicator() {
		return CorporateResellerIndicator;
	}
	public void setCorporateResellerIndicator(String yesNoFlagType) {
		CorporateResellerIndicator = yesNoFlagType;
	}
	public String getReceiveQuoteOutputIndicator() {
		return ReceiveQuoteOutputIndicator;
	}
	public void setReceiveQuoteOutputIndicator(String receiveQuoteOutputIndicator) {
		ReceiveQuoteOutputIndicator = receiveQuoteOutputIndicator;
	}
	public String getOtherPartySiteInstanceIdentifier() {
		return OtherPartySiteInstanceIdentifier;
	}
	public void setOtherPartySiteInstanceIdentifier(String otherPartySiteInstanceIdentifier) {
		OtherPartySiteInstanceIdentifier = otherPartySiteInstanceIdentifier;
	}
	public String getCustomerSegmentCode() {
		return CustomerSegmentCode;
	}
	public void setCustomerSegmentCode(String customerSegmentCode) {
		CustomerSegmentCode = customerSegmentCode;
	}
	public String getLeadCountryCode() {
		return LeadCountryCode;
	}
	public void setLeadCountryCode(String leadCountryCode) {
		LeadCountryCode = leadCountryCode;
	}
	public String getSalesOpportunityIdentifier() {
		return SalesOpportunityIdentifier;
	}
	public void setSalesOpportunityIdentifier(String salesOpportunityIdentifier) {
		SalesOpportunityIdentifier = salesOpportunityIdentifier;
	}
	public String getDealUserTypeCode() {
		return DealUserTypeCode;
	}
	public void setDealUserTypeCode(String dealUserTypeCode) {
		DealUserTypeCode = dealUserTypeCode;
	}
	public String getOtherPartySiteIdentifier() {
		return OtherPartySiteIdentifier;
	}
	public void setOtherPartySiteIdentifier(String otherPartySiteIdentifier) {
		OtherPartySiteIdentifier = otherPartySiteIdentifier;
	}
	public String getValidStartDate() {
		return ValidStartDate;
	}
	public void setValidStartDate(String xmlGregorianCalendar) {
		ValidStartDate = xmlGregorianCalendar;
	}
	public String getIndustryCode() {
		return IndustryCode;
	}
	public void setIndustryCode(String industryCode) {
		IndustryCode = industryCode;
	}
	public String getProductMixCode() {
		return ProductMixCode;
	}
	public void setProductMixCode(String productMixCode) {
		ProductMixCode = productMixCode;
	}
	public String getBusinessRelationshipTypeCode() {
		return BusinessRelationshipTypeCode;
	}
	public void setBusinessRelationshipTypeCode(String businessRelationshipTypeCode) {
		BusinessRelationshipTypeCode = businessRelationshipTypeCode;
	}
	public String getOrganizationIdentifier() {
		return OrganizationIdentifier;
	}
	public void setOrganizationIdentifier(String organizationIdentifier) {
		OrganizationIdentifier = organizationIdentifier;
	}
	public String getDealSourceSystemCode() {
		return DealSourceSystemCode;
	}
	public void setDealSourceSystemCode(String dealSourceSystemCode) {
		DealSourceSystemCode = dealSourceSystemCode;
	}
	public String getDealStatusName() {
		return DealStatusName;
	}
	public void setDealStatusName(String dealStatusName) {
		DealStatusName = dealStatusName;
	}
	public String getCustomerLatinName() {
		return CustomerLatinName;
	}
	public void setCustomerLatinName(String customerLatinName) {
		CustomerLatinName = customerLatinName;
	}
	public String getMiscellaneousChargeCode() {
		return MiscellaneousChargeCode;
	}
	public void setMiscellaneousChargeCode(String miscellaneousChargeCode) {
		MiscellaneousChargeCode = miscellaneousChargeCode;
	}
	public String getDealVersionNumber() {
		return DealVersionNumber;
	}
	public void setDealVersionNumber(String dealVersionNumber) {
		DealVersionNumber = dealVersionNumber;
	}
	public String getRequestType() {
		return RequestType;
	}
	public void setRequestType(String requestType) {
		RequestType = requestType;
	}
	public String getSourceDealTypeCode() {
		return SourceDealTypeCode;
	}
	public void setSourceDealTypeCode(String sourceDealTypeCode) {
		SourceDealTypeCode = sourceDealTypeCode;
	}
	public String getCustomerSatisfactionIndicator() {
		return CustomerSatisfactionIndicator;
	}
	public void setCustomerSatisfactionIndicator(String customerSatisfactionIndicator) {
		CustomerSatisfactionIndicator = customerSatisfactionIndicator;
	}
	public String getCommentType() {
		return CommentType;
	}
	public void setCommentType(String commentType) {
		CommentType = commentType;
	}
	public String getEmployeeIdentifier() {
		return EmployeeIdentifier;
	}
	public void setEmployeeIdentifier(String employeeIdentifier) {
		EmployeeIdentifier = employeeIdentifier;
	}
	public String getLeadBusinessUnitCode() {
		return LeadBusinessUnitCode;
	}
	public void setLeadBusinessUnitCode(String leadBusinessUnitCode) {
		LeadBusinessUnitCode = leadBusinessUnitCode;
	}
	public String getRequestIdentifier() {
		return RequestIdentifier;
	}
	public void setRequestIdentifier(String requestIdentifier) {
		RequestIdentifier = requestIdentifier;
	}
	public String getPriceProtectionIndicator() {
		return PriceProtectionIndicator;
	}
	public void setPriceProtectionIndicator(String priceProtectionIndicator) {
		PriceProtectionIndicator = priceProtectionIndicator;
	}
	public String getFrameworkIndicator() {
		return FrameworkIndicator;
	}
	public void setFrameworkIndicator(String frameworkIndicator) {
		FrameworkIndicator = frameworkIndicator;
	}
	public String getComments() {
		return Comments;
	}
	public void setComments(String comments) {
		Comments = comments;
	}
	public String getNonReusableIndicator() {
		return NonReusableIndicator;
	}
	public void setNonReusableIndicator(String nonReusableIndicator) {
		NonReusableIndicator = nonReusableIndicator;
	}
	public String getCommentSetting() {
		return CommentSetting;
	}
	public void setCommentSetting(String commentSetting) {
		CommentSetting = commentSetting;
	}
	public String getLeadBusinessAreaGroupCode() {
		return LeadBusinessAreaGroupCode;
	}
	public void setLeadBusinessAreaGroupCode(String leadBusinessAreaGroupCode) {
		LeadBusinessAreaGroupCode = leadBusinessAreaGroupCode;
	}
	public String getComplexIndicator() {
		return ComplexIndicator;
	}
	public void setComplexIndicator(String yesNoFlagType) {
		ComplexIndicator = yesNoFlagType;
	}
	public String getRouteToMarketTypeCode() {
		return RouteToMarketTypeCode;
	}
	public void setRouteToMarketTypeCode(String routeToMarketTypeCode) {
		RouteToMarketTypeCode = routeToMarketTypeCode;
	}
	public String getExclusivePartnerIndicator() {
		return ExclusivePartnerIndicator;
	}
	public void setExclusivePartnerIndicator(String exclusivePartnerIndicator) {
		ExclusivePartnerIndicator = exclusivePartnerIndicator;
	}
	public List<DealProductDetail> getDealProductDetails() {
		return DealProductDetails;
	}
	public void setDealProductDetails(List<DealProductDetail> dealProductDetails) {
		DealProductDetails = dealProductDetails;
	}
	public List<DealDetail> getDealDetails() {
		return DealDetails;
	}
	public void setDealDetails(List<DealDetail> dealDetails) {
		DealDetails = dealDetails;
	}
	public List<ResellerDetail> getResellerADetails() {
		return ResellerADetails;
	}
	public void setResellerADetails(List<ResellerDetail> resellerADetails) {
		ResellerADetails = resellerADetails;
	}
	public List<ResellerDetail> getResellerBDetails() {
		return ResellerBDetails;
	}
	public void setResellerBDetails(List<ResellerDetail> resellerBDetails) {
		ResellerBDetails = resellerBDetails;
	}
	public String getLineaddedbyEmployeeNumber() {
		return LineaddedbyEmployeeNumber;
	}
	public void setLineaddedbyEmployeeNumber(String lineaddedbyEmployeeNumber) {
		LineaddedbyEmployeeNumber = lineaddedbyEmployeeNumber;
	}
	public String getTenantID() {
		return TenantID;
	}
	public void setTenantID(String tenantID) {
		TenantID = tenantID;
	}
	public String getSFDCOppID() {
		return SFDCOppID;
	}
	public void setSFDCOppID(String sFDCOppID) {
		SFDCOppID = sFDCOppID;
	}
	
}
