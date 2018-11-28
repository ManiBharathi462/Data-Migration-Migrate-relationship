package com.hpe.s4.integration.service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.MediaType;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.TypeMismatchDataAccessException;

import com.google.gson.Gson;
import com.hp.common.CommonUtil;
import com.hp.common.Constants;
import com.hp.common.FileReqResUtil;
import com.hp.common.NGQThreadLocal;
import com.hp.common.FileReqResUtil.ServiceEnum;
import com.hp.om.beans.ContactType;
import com.hp.om.beans.CustomerType;
import com.hp.om.beans.DealRegistration;
import com.hp.om.beans.EISFormData;
import com.hp.om.beans.KibanaData;
import com.hp.om.beans.LookupConfig;
import com.hp.om.beans.LookupMappingExt;
import com.hp.om.beans.OptimusEclipseMapping;
import com.hp.om.beans.PartnerQuoteAndOPG;
import com.hp.om.beans.QtCmtType;
import com.hp.om.beans.QtFlag;
import com.hp.om.beans.QtFlagType;
import com.hp.om.beans.QtItemFlagType;
import com.hp.om.beans.Quote;
import com.hp.om.beans.QuoteContact;
import com.hp.om.beans.QuoteCustomer;
import com.hp.om.beans.QuoteCustomerAddress;
import com.hp.om.beans.QuoteCustomerContact;
import com.hp.om.beans.QuoteItem;
import com.hp.om.business.dropdownlist.interfaces.DropDownListService;
import com.hp.om.business.email.interfaces.EmailConfigurationService;
import com.hp.om.business.pricing.wngq.util.GSAUtil;
import com.hp.om.business.pricing.wngq.util.PriceCalculatorEnum;
import com.hp.om.integration.masterdata.DecisionTreeANDFilter;
import com.hp.om.integration.masterdata.DecisionTreeORFilter;
import com.hp.om.integration.masterdata.MasterDataConstants;
import com.hp.om.integration.pricing.optimus.OptimusUtil;
import com.hp.om.usermgmt.business.userprofile.interfaces.UserProfileSearchService;
import com.hp.om.usermgmt.services.beans.Group;
import com.hp.om.usermgmt.services.beans.UserProfile;
import com.hp.om.util.QuoteUtil;
import com.hp.service.core.LoggingDomainType;
import com.hp.service.core.Q2CLogger;
import com.hp.service.core.Q2CLoggerFactory;
import com.hpe.s4.deal.beans.CreatedDealDetail;
import com.hpe.s4.deal.beans.DealBundleDetail;
import com.hpe.s4.deal.beans.DealDetail;
import com.hpe.s4.deal.beans.DealItemDetail;
import com.hpe.s4.deal.beans.DealMessages;
import com.hpe.s4.deal.beans.DealProductDetail;
import com.hpe.s4.deal.beans.ResellerDetail;
import com.hpe.s4.deal.beans.S4DealCreationRequest;
import com.hpe.s4.deal.beans.S4DealCreationResponse;
import com.hpe.s4.integration.service.interfaces.S4DealCreationService;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public class S4DealCreationServiceImpl implements S4DealCreationService {

	private static final Q2CLogger LOG = Q2CLoggerFactory.getLogger(S4DealCreationServiceImpl.class,
			LoggingDomainType.OMUI);

	@Value("${edms.clientName}")
	private String clientName;
	@Value("${edms.clientPassword}")
	private String clientPassword;
	@Value("${edms.euvReasonCD}")
	private String euvReasonCD;
	@Value("${edms.euvReasonDesc}")
	private String euvReasonDesc;
	@Value("${edms.euvTypeCD}")
	private String euvTypeCD;
	@Value("${edms.authBasisDesc}")
	private String authBasisDesc;
	@Value("${edms.bundleSource}")
	private String bundleSource;
	@Autowired
	private DropDownListService dropDownListService;
	@Autowired
	public UserProfileSearchService userProfileSearchService;
	@Autowired
	EmailConfigurationService emailService;
	
	private String serviceURL = "https://deal-services-pr-64.its.hpecorp.net/api/deals";
	public static String defaultDealType = "STANDARD";
	private boolean euvSubmitRequired = true;
	public static String cloudProdLine = "FE";
	public static final String  PRODUCT_MIX_NO = "NETWORK_ONLY";
	public static final String  SALES_REP= "Sales Rep";
	
	
	@Override
	public S4DealCreationRequest getS4DealCreationRequest(Quote quote, HashMap<String, Integer> lineItemMap,
			String requestAction, Boolean isWin, String priceTermCD, String addtDis, boolean alternatePriceTermCd,
			EISFormData eisObj) {
		
		
		S4DealCreationRequest s4Dealrequest = new S4DealCreationRequest();	
		
		// set default values for deal creation based on the required deal type
		// HIGH_TOUCH - Create Special Deal
		// HIGH_TOUCH_VERSION - Create Deal Version
		// LOW_TOUCH - Pre Approved Deal - used during completeQuote phase
		// LOW_TOUCH_VERSION - Pre Approved Deal Create Deal Version - used during
		// completeQuote phase
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		Calendar opgCal = Calendar.getInstance();
		opgCal.setTime(quote.getAdditionalInfo().getOpgCreatedDateForHighRisk() != null
				? quote.getAdditionalInfo().getOpgCreatedDateForHighRisk()
				: new Date());

		Date quoteExpiryTs = quote.getExpiryTs();
		if (Constants.NgqConstants.INDIRECT.equalsIgnoreCase(quote.getGoToMarketRoute())) {
			quoteExpiryTs = evaluateQuoteEndDate(quote, requestAction, isWin, cal, opgCal, quoteExpiryTs);
		}
		else {
			if (quote.getAdditionalInfo().isQuoteExtensionRequested()
					&& quote.getAdditionalInfo().getRequestedExtensionts() != null) {
				quoteExpiryTs = quote.getAdditionalInfo().getRequestedExtensionts();
				if (StringUtils.startsWithIgnoreCase(requestAction, "LOW")
						&& quoteExpiryTs.compareTo(quote.getAdditionalInfo().getRequestedExtensionts()) == 0) {
					quote.setExpiryTs(quote.getAdditionalInfo().getRequestedExtensionts());
				}
			}

		}
		s4Dealrequest.setPriceTermCode(priceTermCD);
		s4Dealrequest.setPriceListTypeCode(quote.getPriceGeo());
		
		String dealCreationType = "HIGH_TOUCH";
		String dealApprFl = "N";
		String dealQuoteFl = "N";
		String dealRouteFl = "Y";
		String authStatCD = "N";
		String ignoreDealHeaderUpdates = "N";

		Calendar authDate = null;
		Calendar lastPricedDate = getCalendar(quote.getLastPricedDt(), "lastPricedDate");
		
		
		String dealRequestTypeFlag = "ADD"; // ADD means new shell deal creation
		Integer dealVerToCreate = 1;

		// pre-approved deal
		if (requestAction.equals("LOW_TOUCH")) {
			dealCreationType = "LOW_TOUCH";
			dealApprFl = "Y";
			dealQuoteFl = "Y";
			dealRouteFl = "N";
			authStatCD = "Y";
			authDate = getCalendar(new Date(), "authDate");
		}
		// pre-approved deal version
		if (requestAction.equals("LOW_TOUCH_VERSION")) {
			dealCreationType = "LOW_TOUCH";
			dealApprFl = "Y";
			dealQuoteFl = "Y";
			dealRouteFl = "N";
			authStatCD = "Y";
			authDate = getCalendar(new Date(), "authDate");
			dealRequestTypeFlag = "REPLACE"; // Always use DealRequestType.REPLACE. EDMS determines
											// what needs to be changed and then issues the UPDATE
											// flag internally as part of the versioning process.
			dealVerToCreate = 2;
			if (quote.getAdditionalInfo().getHiTchDealNr() > 0) {
				s4Dealrequest.setSourceDealIdentifier(String.valueOf(quote.getAdditionalInfo().getHiTchDealNr()));
				s4Dealrequest.setRequestType("ADD_UPDATE");
			} else if (quote.getAdditionalInfo().getLowTchDealNr() > 0) {
				s4Dealrequest.setSourceDealIdentifier(String.valueOf(quote.getAdditionalInfo().getLowTchDealNr()));
				s4Dealrequest.setRequestType("ADD_UPDATE");
			}
		}

		// pre-approved deal version
		if (requestAction.equals("LOW_TOUCH_VERSION_ADD")) {
			dealCreationType = "LOW_TOUCH";
			dealApprFl = "Y";
			dealQuoteFl = "Y";
			dealRouteFl = "N";
			authStatCD = "Y";
			ignoreDealHeaderUpdates = "Y";
			authDate = getCalendar(new Date(), "authDate");
			dealRequestTypeFlag = "ADD_UPDATE";
			dealVerToCreate = 2;
			s4Dealrequest.setSourceDealIdentifier(String.valueOf(quote.getDealNr()));
		}

		// ****** For EIS deal ******
		// pre-approved deal
		if (requestAction.equals("EIS_LOW_TOUCH")) {
			dealCreationType = "LOW_TOUCH";
			dealApprFl = "Y";
			dealQuoteFl = "Y";
			dealRouteFl = "N";
			authStatCD = "Y";
			authDate = getCalendar(new Date(), "authDate");
		}

		// pre-approved deal version
		if (requestAction.equals("EIS_LOW_TOUCH_VERSION")) {
			dealCreationType = "LOW_TOUCH";
			dealApprFl = "Y";
			dealQuoteFl = "Y";
			dealRouteFl = "N";
			authStatCD = "Y";
			authDate = getCalendar(new Date(), "authDate");
			//dealRequestTypeFlag = "REPLACE"; // Always use DealRequestType.REPLACE. EDMS determines
											// what needs to be changed and then issues the UPDATE
											// flag internally as part of the versioning process.
			//dealVerToCreate = 2;
			if (quote.getAdditionalInfo().getHiTchDealNr() > 0) {
				s4Dealrequest.setSourceDealIdentifier(String.valueOf(quote.getAdditionalInfo().getHiTchDealNr()));
				dealRequestTypeFlag = "REPLACE";
				dealVerToCreate = 2;
			} else if (quote.getAdditionalInfo().getLowTchDealNr() > 0) {
				s4Dealrequest.setSourceDealIdentifier(String.valueOf(quote.getAdditionalInfo().getLowTchDealNr()));
				dealRequestTypeFlag = "REPLACE";
				dealVerToCreate = 2;
			}
		}

		if (requestAction.equals("EIS_HIGH_TOUCH")) {
			dealCreationType = "HIGH_TOUCH";
			dealApprFl = "N";
			dealQuoteFl = "N";
			dealRouteFl = "Y";
			authStatCD = "N";
			authDate = getCalendar(new Date(), "authDate");
		}

		if (requestAction.equals("EIS_HIGH_TOUCH_VERSION")) {
			dealCreationType = "HIGH_TOUCH";
			dealApprFl = "N";
			dealQuoteFl = "N";
			dealRouteFl = "Y";
			authStatCD = "N";
			authDate = getCalendar(new Date(), "authDate");
			dealRequestTypeFlag ="REPLACE"; // Always use DealRequestType.REPLACE. EDMS determines
											// what needs to be changed and then issues the UPDATE
											// flag internally as part of the versioning process.
			dealVerToCreate = 2;
			// dealRequestSource.setSourceDealNr(quote.getAdditionalInfo().getHiTchDealNr());
			if (quote.getAdditionalInfo().getHiTchDealNr() > 0) {
				s4Dealrequest.setSourceDealIdentifier(String.valueOf(quote.getAdditionalInfo().getHiTchDealNr()));
			} else if (quote.getAdditionalInfo().getLowTchDealNr() > 0) {
				s4Dealrequest.setSourceDealIdentifier(String.valueOf(quote.getAdditionalInfo().getLowTchDealNr()));
			}
		}

		if (requestAction.equals("HIGH_TOUCH_VERSION")) {
			dealRequestTypeFlag = "REPLACE"; // Always use DealRequestType.REPLACE. EDMS determines
											// what needs to be changed and then issues the UPDATE
											// flag internally as part of the versioning process.
			dealVerToCreate = 2;
			// dealRequestSource.setSourceDealNr(quote.getAdditionalInfo().getHiTchDealNr());
			if (quote.getAdditionalInfo().getHiTchDealNr() > 0) {
				s4Dealrequest.setSourceDealIdentifier(String.valueOf(quote.getAdditionalInfo().getHiTchDealNr()));
			} else if (quote.getAdditionalInfo().getLowTchDealNr() > 0) {
				s4Dealrequest.setSourceDealIdentifier(String.valueOf(quote.getAdditionalInfo().getLowTchDealNr()));
			}
		}

		if (requestAction.equals("HIGH_TOUCH_VERSION_ADD")) {
			ignoreDealHeaderUpdates = "Y";
			dealRequestTypeFlag = "ADD_UPDATE";
			dealVerToCreate = 2;
			s4Dealrequest.setSourceDealIdentifier(String.valueOf(quote.getDealNr()));
		}
		LOG.debug("ngqEDMServiceImpl.requestAction : " + requestAction + ":" + isWin);
		
		//----------USING GENERATED ECLIPSE STUBS-----------------
//		EmployeeType employee = getEmployee(quote.getContacts().get(ContactType.QUOTE_OWNER), "QUOTE_OWNER",
//				quote.getCreatedBy());
		String employee = getEmployee(quote.getContacts().get(ContactType.QUOTE_OWNER), "QUOTE_OWNER",
				quote.getCreatedBy());
		s4Dealrequest.setDealSourceSystemCode(clientName);
		s4Dealrequest.setDealSourceSystemCode("NGQ");
		s4Dealrequest.setSourceDealTypeCode(dealCreationType);
		s4Dealrequest.setDealSourceSystemIdentifier(quote.getAssetQuoteNr());
		Integer version = Integer.valueOf(quote.getAssetQuoteVrsn());
		s4Dealrequest.setSourceDealVersionNumber(quote.getAssetQuoteVrsn());
//#N/A		dealRequestSource.setRequestType(dealRequestTypeFlag);
		s4Dealrequest.setRequestType(dealRequestTypeFlag);
		LOG.debug("ngqEDMServiceImpl.setRequestType : " + dealRequestTypeFlag);
		LOG.debug("ngqEDMServiceImpl.getContractStartDate : " + quote.getContractStartDate());
		LOG.debug("ngqEDMServiceImpl.getContractEndDate : " + quote.getContractEndDate());
		LOG.debug("ngqEDMServiceImpl.getEffectiveTs : " + quote.getEffectiveTs());
		LOG.debug("ngqEDMServiceImpl.getOpportunityId : " + quote.getOpportunityId());
		LOG.debug("ngqEDMServiceImpl.getCountryCode : " + quote.getCountryCode());
		LOG.debug("ngqEDMServiceImpl.getCurrencyCd : " + quote.getCurrencyCd());
		LOG.debug("ngqEDMServiceImpl.getExpiryTs : " + quoteExpiryTs);
		LOG.debug("ngqEDMServiceImpl.getPriceListType : " + quote.getPriceListType());
		LOG.debug("ngqEDMServiceImpl.getPriceTermCd : " + quote.getPriceTermCd() + ":" + priceTermCD);
		LOG.debug("ngqEDMServiceImpl.getGoToMarketRoute : " + quote.getGoToMarketRoute());
		
		s4Dealrequest.setAutomatedSalesApprovalProcessIndicat("");
		String startDate = dateToXMLGregorianCalendar(getCalendar(quote.getEffectiveTs(), "effectiveTs").getTime()).toString();
		if(startDate != null)
		s4Dealrequest.setValidStartDate(startDate.substring(0, startDate.length()-1));
		
		if (quote.getFlags().get(QtFlagType.CIPPGMQUOTE) != null
				&& quote.getFlags().get(QtFlagType.CIPPGMQUOTE).getFlgVl() != null
				&& quote.getFlags().get(QtFlagType.CIPPGMQUOTE).getFlgVl().equals("true")){
//#N/A			dealHeader.setBusModelCD(Constants.NgqConstants.CIP_PROGRAM_QUOTE);
		}
		
		// ys - fix CR# 183257
		// dealHeader.setBusModelCD(busModelCD); // TODO Need to find Lead Biz Group.
		// Hence commenting for now.
//#N/A		dealHeader.setCRMOppID(quote.getOpportunityId());
		s4Dealrequest.setComplexIndicator("N");
		s4Dealrequest.setConflictCheckIndicator("Y");
		s4Dealrequest.setCorporateResellerIndicator("Y");
		s4Dealrequest.setLeadCountryCode(quote.getCountryCode());
		s4Dealrequest.setCurrencyCode(dropDownListService.getEDMCurrCd(quote.getCurrencyCd()));
		if (quote.getEcommerceCustomer()) {
			s4Dealrequest.setCatalogIndicator(quote.getCatalogMaintenanceQuote() ? "Y": "N");
		}
		
		if (StringUtils.isNotEmpty(quote.getDealType())) {
			s4Dealrequest.setDealTypeCode(quote.getDealType());
		} else {
			s4Dealrequest.setDealTypeCode(defaultDealType);
		}
		s4Dealrequest.setDealTypeCode("ZSND");
		int maxLength = 120;
		String dealDesc= quote.getAssetQuoteNrAndVrsn() + "-" + quote.getName();
		if(dealDesc!=null && dealDesc.length()>120){
		dealDesc=dealDesc.substring(0, maxLength);
		}
//		s4Dealrequest.setDealDescription(dealDesc);
		s4Dealrequest.setDealDescription(dealDesc.replaceAll(" ", ""));
		if (eisObj != null && eisObj.getDealEndDt() != null) {
			String endDate =dateToXMLGregorianCalendar(getCalendar(eisObj.getDealEndDt(), "expiryTs").getTime()).toString();
			if(endDate != null)
			s4Dealrequest.setValidEndDate(endDate.substring(0, endDate.length()-1));
		} else {
			String endDate =dateToXMLGregorianCalendar(getCalendar(quoteExpiryTs, "expiryTs").getTime()).toString();
			if(endDate != null)
			s4Dealrequest.setValidEndDate(endDate.substring(0, endDate.length()-1));
		}

		if (quote.getAdditionalInfo() == null || quote.getAdditionalInfo().getCustomerEngagementModel() == null
				|| quote.getAdditionalInfo().getCustomerEngagementModel().equals("")) {
			throw (new TypeMismatchDataAccessException("Engagement model is empty."));
		} else {
			LOG.debug("ngqEDMServiceImpl.getCustomerEngagementModel : "
					+ quote.getAdditionalInfo().getCustomerEngagementModel());
				s4Dealrequest.setCustomerEngagementTypeName(quote.getAdditionalInfo().getCustomerEngagementModel());
		}
		
		// dealHeader.setLeadBusGroup(leadBusGroup);
		// dealHeader.setLeadBusUnit(leadBusUnit);
		s4Dealrequest.setMiscellaneousChargeCode(quote.getAdditionalInfo().getFulFillmentType());
		// LOG.debug("ngqEDMServiceImpl.getPaymentTerm : " + quote.getPaymentTerm() + "
		// : " + quote.getPaymentTermDesc());

//#N/A		dealHeader.setPIUFl(YesNoFlagType.fromValue("N"));
		if (quote.getDealType() != null){
			if(StringUtils.equalsIgnoreCase(Constants.DealType.TACTICAL_GIVEAWAY, quote.getDealType())) {
				s4Dealrequest.setMiscellaneousChargeCode(quote.getAdditionalInfo().getEscalationCode());
			}else if(StringUtils.equalsIgnoreCase(Constants.DealType.Internal_Use, quote.getDealType())){
//#N/A				dealHeader.setPIUFl(YesNoFlagType.fromValue("N"));
			}
		}
		if (quote.getPaymentTermDesc() != null && !quote.getPaymentTermDesc().equals("")) {
			s4Dealrequest.setPaymentTermCode(quote.getPaymentTermDesc());
			//s4Dealrequest.setPaymentTermCode("DP");
		}
		
		s4Dealrequest.setPriceListTypeCode(quote.getPriceGeo());

		// dealHeader.setPriceTermCD(quote.getPriceTermCd());
		s4Dealrequest.setPriceTermCode(priceTermCD);

		if(quote.getGoToMarketRoute().equalsIgnoreCase("SI")||quote.getGoToMarketRoute().equalsIgnoreCase("Direct")){
			
			addSIRequestDetails(s4Dealrequest, quote, eisObj);
		
		}

		// dealHeader.setProtectionCD("Y");
		if (quote.getGoToMarketRoute() == null || quote.getGoToMarketRoute().equals("")) {
			throw (new TypeMismatchDataAccessException("GoToMarketRoute is empty."));
		} else if (quote.getGoToMarketRoute().equalsIgnoreCase("Indirect")) {
			// set Indirect additional request values
			s4Dealrequest.setRouteToMarketTypeCode("Indirect");
			addIndirectRequestDetails(s4Dealrequest, quote, eisObj);
		} else {
			s4Dealrequest.setRouteToMarketTypeCode(quote.getGoToMarketRoute());
		}
		// Adding Rebate type value as requested by Rajandran from Eclipse for testing
		// purpose
		if (quote.getAdditionalInfo().getFulFillmentType() != null) {
			if ("72R".equalsIgnoreCase(quote.getAdditionalInfo().getFulFillmentType())) {
				s4Dealrequest.setDiscountTypeCode("Rebate");
			} else {
				s4Dealrequest.setDiscountTypeCode("Up Front");
			}
		}
		if (quote.getDealType() != null
				&& StringUtils.equalsIgnoreCase(Constants.DealType.TACTICAL_GIVEAWAY, quote.getDealType())) {
			s4Dealrequest.setDiscountTypeCode("Not Allowed");
		}
		// dealHeader.setRoutingIndicator(routingIndicator);
		if(null != quote.getOpportunityId()){
			s4Dealrequest.setSalesForceCaseNumber(quote.getOpportunityId().replace("OPE-", ""));
			s4Dealrequest.setSFDCOppID(quote.getOpportunityId());
		}
		else{
			s4Dealrequest.setSalesForceCaseNumber("0");
			s4Dealrequest.setSFDCOppID("0");
		}
		
//#N/A		dealHeader.setSingleCompanyDeal(YesNoFlagType.Y);

		if (alternatePriceTermCd) {
//#N/A				dealHeader.setSuppressListPriceRefreshFl(YesNoFlagType.Y);
			String dealComment = "Deal was priced with alternate price term ";

			if (quote.getIncoterm() == null) {
				dealComment = dealComment + quote.getPriceListType();
			} else {
				dealComment = dealComment + quote.getPriceTermCd();
			}
			dealComment = dealComment + ". List price refresh is suppressed in Eclipse.";
			LOG.debug("ngqEDMServiceImpl.dealComment : " + dealComment);
			addComment(s4Dealrequest, quote, dealComment, false);

		} else if (GSAUtil.fedSledAlternatePDForEscalatedPricing(quote) == true) {
//#N/A			dealHeader.setSuppressListPriceRefreshFl(YesNoFlagType.Y);
			String dealComment = "Deal was priced with alternate price term "
					+ quote.getAdditionalInfo().getOrigPriceListType()
					+ ". List price refresh is suppressed in Eclipse.";
			LOG.debug("ngqEDMServiceImpl.dealComment : " + dealComment);
			addComment(s4Dealrequest, quote, dealComment, false);
		} else {
//#N/A			dealHeader.setSuppressListPriceRefreshFl(YesNoFlagType.N);
		}
//#N/A			dealHeader.setTenantID("HPE");
		s4Dealrequest.setTenantID("HPE");
//		dealRequest.setDealHeader(dealHeader);
//#N/A		dealActions.setDealApprFl(dealApprFl);
//#N/A		dealActions.setDealQuoteFl(dealQuoteFl);
//#N/A		dealActions.setDealRouteFl(dealRouteFl);
		s4Dealrequest.setDealVersionNumber(dealVerToCreate.toString());
//#N/A		dealActions.setQuotedByHPEmployee(employee);
//#N/A		dealActions.setIgnoreDealHeaderUpdates(ignoreDealHeaderUpdates);

		// Added by september team
		Map<QtFlagType, QtFlag> flags = quote.getFlags();
		QtFlag flag = flags.get(QtFlagType.HIGHRISKDEALFLAG);
		if (flag != null) {
			String highrisk = flag.getFlgVl();
		}
		if (isWin) {
//#N/A			dealActions.setIgnoreHighRiskValidations(YesNoFlagType.Y);
		}
		boolean dealRegApplied = false;
		dealRegApplied = checkIfDealRegShouldBeApplied(quote, requestAction, isWin, dealRegApplied);
//#N/A		dealActions.setPerformDealRegAdjustmentsFl(YesNoFlagType.N);

		if (isWin == true)
		{
			Group loggedInUserGrp = null;
			UserProfile up = userProfileSearchService.getCurrentUserProfile("");
			if(up  !=null && up.getGroupList() !=null && up.getGroupList().size() > 0) {
			loggedInUserGrp = up.getGroupList().get(0);
			}

			if(loggedInUserGrp !=null && Constants.NgqConstants.INDIRECT.equalsIgnoreCase(quote.getGoToMarketRoute()) && (Constants.NgqConstants.NGQ_SALES_REP.equals(loggedInUserGrp.getGroupName()) ||Constants.NgqConstants.NGQ_SALES_OP.equals(loggedInUserGrp.getGroupName())))
			{
				s4Dealrequest.setProductMixCode(PRODUCT_MIX_NO);
				s4Dealrequest.setDealCreatorCode(SALES_REP);
			}

//#N/A		dealWonLostStat.setGenerateOPGNbrFl(YesNoFlagType.Y);
		s4Dealrequest.setDealStatusName("W");
		}
		
		if ("Indirect".equalsIgnoreCase(quote.getGoToMarketRoute())) {
			if (quote.getAdditionalInfo().getJustificationType() != null
					&& quote.getAdditionalInfo().getCompetitor() != null) {
				String busJustifySubject = quote.getAdditionalInfo().getJustificationType() + " - "
						+ quote.getAdditionalInfo().getCompetitor();
//#N/A				dealBusJustification.setBusJustifySubject(busJustifySubject);
//#N/A				dealRequest.setDealBusJustification(dealBusJustification);
			}
		}

//2.2		DealEUVStatType dealEUVStat = new DealEUVStatType();
//		dealEUVStat.setEUVCompletionDate(dateToXMLGregorianCalendar(getCalendar(new Date(), "EUVCompletionDate").getTime()));
////#N/A		dealEUVStat.setEUVCompletionHPEmployee(employee);
//		dealEUVStat.setEUVReasonCD(euvReasonCD);
//		dealEUVStat.setEUVReasonDesc(euvReasonDesc);
//		dealEUVStat.setEUVTypeCD(euvTypeCD);
		// Removing EUV part from deal creation request - As requested by Andrew and
		// confirmation from Karthik - 07-Nov-2017
		// Adding back EUV as part of EIS failure on 13-11-2017
//		dealRequest.setDealEUVStat(dealEUVStat);

		LOG.debug("ngqEDMServiceImpl.getMdcpOrgId : " + quote.getCustomers().get(CustomerType.SOLDTO).getMdcpOrgId());
		LOG.debug("ngqEDMServiceImpl.getOtrPrtySiteInsnId : "
				+ quote.getCustomers().get(CustomerType.SOLDTO).getOtrPrtySiteInsnId());
		LOG.debug("ngqEDMServiceImpl.getOtrPrtySiteId : "
				+ quote.getCustomers().get(CustomerType.SOLDTO).getOtrPrtySiteId());
		// dealCustomer.setCustSegment(custSegment);
		try {

			if (addtDis.equalsIgnoreCase(Constants.CommonConstants.ADDTDIC)) {
				QuoteUtil util = new QuoteUtil();
				QuoteCustomer reseller = util.getPrimaryReseller(quote);
				if (reseller!=null && reseller.getMdcpOrgId() != null) {
					s4Dealrequest.setOrganizationIdentifier(reseller.getMdcpOrgId().toString());
				} else {
					LOG.info("DealCreationResultType errorMessage: Reseller Mdcp Org Id is missing");
					String msg1 = "Deal creation error. Reseller Mdcp Org Id is missing";
					String lclAddStatusMessages = CommonUtil.getLoclizedMsg("NGEEDM_DL_ERR_RESLR_MDCP_MIS", null, msg1);
					quote.addStatusMessages("Exception", lclAddStatusMessages);
					// OPG Creation Failed
					if (isWin && isNotHighRiskDeal(quote)) {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.OPG_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.OPG_CREATION_FAILURE);
					} else {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.DEAL_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.DEAL_CREATION_FAILURE);
					}
					return null;
				}
				if (reseller !=null && reseller.getOtrPrtySiteInsnId() != null) {
					s4Dealrequest.setOtherPartySiteIdentifier(reseller.getOtrPrtySiteInsnId().toString());
				} else {
					LOG.info("DealCreationResultType errorMessage: Reseller Mdcp Other Party Site Instance Id is missing");
					String msg1 = "Deal creation error. Reseller Mdcp Other Party Site Instance Id is missing";
					String lclAddStatusMessages = CommonUtil.getLoclizedMsg("NGEEDM_DL_ERR_RESLR_MDCP_OP_SITE_ID_MIS",
							null, msg1);
					quote.addStatusMessages("Exception", lclAddStatusMessages);
					// OPG Creation Failed
					if (isWin && isNotHighRiskDeal(quote)) {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.OPG_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.OPG_CREATION_FAILURE);
					} else {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.DEAL_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.DEAL_CREATION_FAILURE);
					}
					return null;
				}
				
			} else {
				if (quote.getAdditionalInfo().getPriceBasedOn() == 1
						&& quote.getCustomers().get(CustomerType.SOLDTO).getMdcpOrgId() != null) {
					s4Dealrequest.setOrganizationIdentifier(quote.getCustomers().get(CustomerType.SOLDTO).getMdcpOrgId().toString());
				} else if (quote.getAdditionalInfo().getPriceBasedOn() == 2
						&& quote.getCustomers().get(CustomerType.SHIPTO).getMdcpOrgId() != null) {
					s4Dealrequest.setOrganizationIdentifier(quote.getCustomers().get(CustomerType.SHIPTO).getMdcpOrgId().toString());
				} else if (quote.getAdditionalInfo().getPriceBasedOn() == 3
						&& quote.getCustomers().get(CustomerType.ENDCUSTOMER).getMdcpOrgId() != null) {
					s4Dealrequest.setOrganizationIdentifier(quote.getCustomers().get(CustomerType.ENDCUSTOMER).getMdcpOrgId().toString());
				} else {
					LOG.info("DealCreationResultType errorMessage: Customer Mdcp Org Id is missing");
					String msg1 = "Deal creation error. Customer Mdcp Org Id is missing";
					String lclAddStatusMessages = CommonUtil.getLoclizedMsg("NGEEDM_DL_ERR_CUST_MDCP_MIS", null, msg1);
					quote.addStatusMessages("Exception", lclAddStatusMessages);
					// OPG Creation Failed
					if (isWin && isNotHighRiskDeal(quote)) {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.OPG_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.OPG_CREATION_FAILURE);
					} else {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.DEAL_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.DEAL_CREATION_FAILURE);
					}
					return null;
				}
				if (quote.getAdditionalInfo().getPriceBasedOn() == 1
						&& quote.getCustomers().get(CustomerType.SOLDTO).getOtrPrtySiteInsnId() != null) {
					s4Dealrequest.setOtherPartySiteInstanceIdentifier(quote.getCustomers().get(CustomerType.SOLDTO).getOtrPrtySiteInsnId().toString());
				} else if (quote.getAdditionalInfo().getPriceBasedOn() == 2
						&& quote.getCustomers().get(CustomerType.SHIPTO).getOtrPrtySiteInsnId() != null) {
					s4Dealrequest.setOtherPartySiteInstanceIdentifier(quote.getCustomers().get(CustomerType.SHIPTO).getOtrPrtySiteInsnId().toString());
				} else if (quote.getAdditionalInfo().getPriceBasedOn() == 3
						&& quote.getCustomers().get(CustomerType.ENDCUSTOMER).getOtrPrtySiteInsnId() != null) {
					s4Dealrequest.setOtherPartySiteInstanceIdentifier(quote.getCustomers().get(CustomerType.ENDCUSTOMER).getOtrPrtySiteInsnId().toString());
				} else {
					LOG.info("DealCreationResultType errorMessage: Customer Mdcp Other Party Site Instance Id is missing");
					String msg1 = "Deal creation error. Customer Mdcp Other Party Site Instance Id is missing";
					String lclAddStatusMessages = CommonUtil.getLoclizedMsg("NGEEDM_DL_ERR_CUST_MDCP_OP_SITE_ID_MIS",
							null, msg1);
					quote.addStatusMessages("Exception", lclAddStatusMessages);
					// OPG Creation Failed
					if (isWin && isNotHighRiskDeal(quote)) {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.OPG_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.OPG_CREATION_FAILURE);
					} else {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.DEAL_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.DEAL_CREATION_FAILURE);
					}
					return null;
				}
				if (quote.getAdditionalInfo().getPriceBasedOn() == 1
						&& quote.getCustomers().get(CustomerType.SOLDTO).getOtrPrtySiteId() != null) {
					s4Dealrequest.setOtherPartySiteIdentifier(quote.getCustomers().get(CustomerType.SOLDTO).getOtrPrtySiteId().toString());
				} else if (quote.getAdditionalInfo().getPriceBasedOn() == 2
						&& quote.getCustomers().get(CustomerType.SHIPTO).getOtrPrtySiteId() != null) {
					s4Dealrequest.setOtherPartySiteIdentifier(quote.getCustomers().get(CustomerType.SHIPTO).getOtrPrtySiteId().toString());
				} else if (quote.getAdditionalInfo().getPriceBasedOn() == 3
						&& quote.getCustomers().get(CustomerType.ENDCUSTOMER).getOtrPrtySiteId() != null) {
					s4Dealrequest.setOtherPartySiteIdentifier(quote.getCustomers().get(CustomerType.ENDCUSTOMER).getOtrPrtySiteId().toString());
				} else {
					LOG.info("DealCreationResultType errorMessage: Customer Mdcp Other Party Site Id is missing");
					String msg1 = "Deal creation error. Customer Mdcp Other Party Site Id is missing";
					String lclAddStatusMessages = CommonUtil.getLoclizedMsg("NGEEDM_DL_ERR_NO_LN_RESP", null, msg1);
					quote.addStatusMessages("Exception", lclAddStatusMessages);
					// OPG Creation Failed
					if (isWin && isNotHighRiskDeal(quote)) {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.OPG_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.OPG_CREATION_FAILURE);
					} else {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.DEAL_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.DEAL_CREATION_FAILURE);
					}
					return null;
				}
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//#N/A		dealCustomer.setMDCPCustSegment(quote.getCustomers().get(CustomerType.SOLDTO).getCustSegCd());
		//dealCustomer.setMDCPCustSegment(MDCPCustSegment);
		// dealCustomer.setContractID(contractID);
		s4Dealrequest.setBusinessRelationshipTypeCode("E");
//		dealRequest.setDealCustomer(dealCustomer);

		String srEmployee = "";
		if (quote.getContacts().get(ContactType.SALES_REP) != null
				&& quote.getContacts().get(ContactType.SALES_REP).getEmail() != null
				&& !(quote.getContacts().get(ContactType.SALES_REP).getEmail().equals(""))) {
			srEmployee = getEmployee(quote.getContacts().get(ContactType.SALES_REP), "SALES_REP", quote.getCreatedBy());

			// CR-311862 - Ansari - Sending quote owner if sales rep is not in hpe or hp
			// domain
			if (!srEmployee.contains("@hpe.com")
					&& !srEmployee.contains("@hp.com")) {
				srEmployee = getEmployee(quote.getContacts().get(ContactType.QUOTE_OWNER), "QUOTE_OWNER",
						quote.getCreatedBy());
			}

		} else {
			srEmployee = getEmployee(quote.getContacts().get(ContactType.QUOTE_OWNER), "QUOTE_OWNER",
					quote.getCreatedBy());
		}
		
//		DealHPUserType[] dealHPUserList = new DealHPUserType[4];
//		if(quote.getGoToMarketRoute().equalsIgnoreCase("SI")){
//			dealHPUserList[0] = getDealHPUser("DL", employee);
//			}
//		else{
//		dealHPUserList[0] = getDealHPUser("DL", new EmployeeType());
//		}
//
//		dealHPUserList[1] = getDealHPUser("DC", employee);
//		dealHPUserList[2] = getDealHPUser("SR", srEmployee);
//		dealHPUserList[3] = getDealHPUser("DM", employee);
//
//		ArrayOfDealHPUserType arrayOfDealHPUserType=new ArrayOfDealHPUserType();
//		arrayOfDealHPUserType.getDealHPUser().addAll(Arrays.asList(dealHPUserList));

//		dealRequest.setHPUsers(arrayOfDealHPUserType);
		
		s4Dealrequest.setDealUserTypeCode("DL");

		if (quote.getComments().get(QtCmtType.DEALCOMMENTSCURR) != null
				&& quote.getComments().get(QtCmtType.DEALCOMMENTSCURR).getCmtTxt1() != null
				&& !(quote.getComments().get(QtCmtType.DEALCOMMENTSCURR).getCmtTxt1().equals(""))) {
			addComment(s4Dealrequest, quote,quote.getComments().get(QtCmtType.DEALCOMMENTSCURR).getCmtTxt1(),false);
		}
		if (quote.getComments().get(QtCmtType.DEALCOMMENTSEXT) != null
				&& quote.getComments().get(QtCmtType.DEALCOMMENTSEXT).getCmtTxt1() != null
				&& !(quote.getComments().get(QtCmtType.DEALCOMMENTSEXT).getCmtTxt1().equals(""))) {
			addOtherComment(s4Dealrequest, quote, quote.getComments().get(QtCmtType.DEALCOMMENTSEXT).getCmtTxt1(), true, null);
		}

		if (quote.getComments().get(QtCmtType.PREFERENTIALPRICING) != null
				&& quote.getComments().get(QtCmtType.PREFERENTIALPRICING).getCmtTxt1() != null
				&& !(quote.getComments().get(QtCmtType.PREFERENTIALPRICING).getCmtTxt1().equals(""))
				&& requestAction != null && requestAction.contains("HIGH_TOUCH")) {
			addOtherComment(s4Dealrequest, quote,quote.getComments().get(QtCmtType.PREFERENTIALPRICING).getCmtTxt1(), false, "PE");
		}

		if (quote.getFlags() != null && quote.getFlags().get(QtFlagType.DISTRIBUTORCHANGE) != null
				&& quote.getFlags().get(QtFlagType.DISTRIBUTORCHANGE).getFlgVl() != null
				&& quote.getFlags().get(QtFlagType.DISTRIBUTORCHANGE).getFlgVl().equalsIgnoreCase("true")) {
			addDistiChangeComment(s4Dealrequest, quote, "Distributor Changed");
		}

		if (quote.getComments().get(QtCmtType.TACTICALCOMMENT) != null
				&& quote.getComments().get(QtCmtType.TACTICALCOMMENT).getCmtTxt1() != null
				&& !(quote.getComments().get(QtCmtType.TACTICALCOMMENT).getCmtTxt1().equals(""))) {
			addOtherComment(s4Dealrequest, quote,quote.getComments().get(QtCmtType.TACTICALCOMMENT).getCmtTxt1(), false, "TATG");
		}

		if (quote.getGoToMarketRoute().equalsIgnoreCase("Indirect")) {
			// set Indirect comments
			addIndirectCommentDetails(s4Dealrequest, quote);
		}

		LinkedHashMap<String, ArrayList<QuoteItem>> configs = new LinkedHashMap<String, ArrayList<QuoteItem>>();
		ArrayList<QuoteItem> alstandalones = new ArrayList<QuoteItem>();
		ArrayList<QuoteItem> cloudOnes = new ArrayList<QuoteItem>();
		ArrayList<QuoteItem> autoBundledItems = new ArrayList<QuoteItem>();
		Map<String, String> configdmapping = new HashMap<>();
		String configId;
		Set<String> autoBundlePLSet = getAutoBundleCheck(quote);
		for (QuoteItem qtitem : quote.getItems()) {
			if (("Product").equalsIgnoreCase(qtitem.getLineTypeCd())
					|| "Option".equalsIgnoreCase(qtitem.getLineTypeCd())
					|| ("Config").equalsIgnoreCase(qtitem.getLineTypeCd())
					|| "Bundle".equalsIgnoreCase(qtitem.getLineTypeCd())) {
				// CR 330614 is fixed
				if (Constants.UserAndAdminConfiguration.NGQ_TEAM
						.equalsIgnoreCase(quote.getAdditionalInfo().getQtTyp())) {
					if (qtitem.getLclUntNtAmt() != null) {
						qtitem.getAdditionalInfo().setOriginalNetPrice(qtitem.getLclUntNtAmt());
					} else {
						qtitem.getAdditionalInfo().setOriginalNetPrice(BigDecimal.valueOf(0));
					}
				}
				LOG.debug("ngqEDMServiceImpl.getProductNr : " + qtitem.getProductNr());
				LOG.debug("ngqEDMServiceImpl.getPriceSourceID : " + qtitem.getPriceSourceID());
				LOG.debug("ngqEDMServiceImpl.getLineTypeCd : " + qtitem.getLineTypeCd());
				LOG.debug("ngqEDMServiceImpl.getPriceSourceCode : " + qtitem.getPriceSourceCode());
				LOG.debug("ngqEDMServiceImpl.getBundleId : " + qtitem.getBundleId());
				LOG.debug("ngqEDMServiceImpl.getBndlTyp : " + qtitem.getAdditionalInfo().getBndlTyp());
				LOG.debug("ngqEDMServiceImpl.getItemMccs : " + qtitem.getItemMccs());
				LOG.debug("ngqEDMServiceImpl.getCnfgnSolId : " + qtitem.getCnfgnSolId());
				LOG.debug("ngqEDMServiceImpl.getCnfgnSysId : " + qtitem.getCnfgnSysId());
				LOG.debug("ngqEDMServiceImpl.getCnfgnSystemName : " + qtitem.getCnfgnSystemName());
				LOG.debug("ngqEDMServiceImpl.getLineItemRowId : " + qtitem.getLineItemRowId());
				LOG.debug("ngqEDMServiceImpl.getSlsQtnItmSqnNr : " + qtitem.getSlsQtnItmSqnNr());
				LOG.debug("ngqEDMServiceImpl.getSlsQtnRvsnSqnNr : " + qtitem.getSlsQtnRvsnSqnNr());
				LOG.debug("ngqEDMServiceImpl.getSlsQtnVrsnSqnNr : " + qtitem.getSlsQtnVrsnSqnNr());

				LOG.debug("ngqEDMServiceImpl.getUcid : " + qtitem.getUcid());
				LOG.debug("ngqEDMServiceImpl.getUcLineItemId : " + qtitem.getUcLineItemId());
				LOG.debug("ngqEDMServiceImpl.getUcSubconfiglineitemSequence : "
						+ qtitem.getUcSubconfiglineitemSequence());
				LOG.debug("ngqEDMServiceImpl.getCnfgnLineItemSeq : " + qtitem.getCnfgnLineItemSeq());
				/*if ((("Product").equalsIgnoreCase(qtitem.getLineTypeCd())
						|| "Option".equalsIgnoreCase(qtitem.getLineTypeCd()))
						&& (qtitem.getProductLine() == null || qtitem.getProductLine().equals(""))) {

					// Code to capture ProductLine from MaterialInfo.
					
						ProductFromS4Request request = new ProductFromS4Request();
						if("Option".equalsIgnoreCase(qtitem.getLineTypeCd()) && !qtitem.getProductOption().isEmpty()) {
						request.setMaterialIdentifier(qtitem.getProductNr()+"#"+qtitem.getProductOption());
						}else {
							request.setMaterialIdentifier(qtitem.getProductNr());
						}
						
						Gson gson = new GsonBuilder().create();
						String jsonObject = gson.toJson(request);
						System.out.println("jsonFromGson : "+jsonObject.toString());
						
						S4ConnectionDao conn = new S4ConnectionDao();
						String response = conn.sendPostReqToS4("https://pricing-itg.its.hpecorp.net/api/fetch-product-details", jsonObject);
						
							ProductFromS4Response  responseObject = new ProductFromS4Response();
							
							
							Gson gson1 = new Gson();
							gson1.toJson(responseObject);

							// from JSON to object 
							responseObject = gson1.fromJson(response.toString(),ProductFromS4Response.class);
							qtitem.setProductLine(responseObject.getMaterialInfo().getSalesDivisionCode());
						
				}*/
				
				if ((("Product").equalsIgnoreCase(qtitem.getLineTypeCd())
						|| "Option".equalsIgnoreCase(qtitem.getLineTypeCd()))
						&& (qtitem.getProductLine() == null || qtitem.getProductLine().equals(""))) {
					LOG.info("DealCreationResultType errorMessage: Product " + qtitem.getProductId()
							+ " is missing a required Product Line");
					String currmsg = "Product " + qtitem.getProductId() + " is missing a required Product Line";
					String lclerrmsg = CommonUtil.getLoclizedMsg("NGQEDM_PDT_MISLN",
							new String[] { qtitem.getProductId() }, currmsg);
					quote.addStatusMessages("Exception", lclerrmsg);
					// OPG Creation Failed
					if (isWin && isNotHighRiskDeal(quote)) {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.OPG_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.OPG_CREATION_FAILURE);
					} else {
						// emailService.configureEmail(quote,
						// Constants.EmailScenario.DEAL_CREATION_FAILURE);
						this.exeEmailInThread(quote, Constants.EmailScenario.DEAL_CREATION_FAILURE);
					}
					return null;
				}

				// if ((qtitem.getAdditionalInfo() != null &&
				// qtitem.getAdditionalInfo().getBndlTyp() != null &&
				// qtitem.getAdditionalInfo().getBndlTyp().equalsIgnoreCase("S"))) {
				if (qtitem.getAdditionalInfo() != null && qtitem.getAdditionalInfo().getBndlTyp() != null
						&& qtitem.getAdditionalInfo().getBndlTyp() != "") {
					// bundles - O, S, T
					// For eCommmerce/Catalog Quote, do not override
					// BundleId(origAssetConfigId/configId from CIDS) with CnfgnSysId/UCID. TODO:::
					// Is this new change can be applicable for all cases or only for Catalog
					// Quotes??
					if(("Direct").equalsIgnoreCase(quote.getGoToMarketRoute())) {
						setBundledIdforEcomm(qtitem, configdmapping);
					} else {
						qtitem.setBundleId(qtitem.getCnfgnSysId());

					}
					// qtitem.setBundleItemNr(qtitem.getUcLineItemId());
					if (configs.containsKey(qtitem.getBundleId())) {
						configs.get(qtitem.getBundleId()).add(qtitem);
					} else {
						ArrayList<QuoteItem> altemp = new ArrayList<QuoteItem>();
						altemp.add(qtitem);
						configs.put(qtitem.getBundleId(), altemp);
					}
				} else {
					// For eCommmerce/Catalog Quote, do not override
					// BundleId(origAssetConfigId/configId from CIDS) with CnfgnSysId/UCID. TODO:::
					// Is this new change can be applicable for all cases or only for Catalog
					// Quotes??
					
					if(("Direct").equalsIgnoreCase(quote.getGoToMarketRoute())) {
						setBundledIdforEcomm(qtitem, configdmapping);
					} else {
						if (qtitem.getIconId() != null && !qtitem.getIconId().equals("")) {
							qtitem.setBundleId(qtitem.getIconId());
						} else {
							qtitem.setBundleId(qtitem.getUcid());
						}
					}

					qtitem.setBundleItemNr(qtitem.getUcLineItemId());
					if (qtitem.getBundleId() != null) {
						// cto's
						if (configs.containsKey(qtitem.getBundleId())) {
							configs.get(qtitem.getBundleId()).add(qtitem);
						} else {
							ArrayList<QuoteItem> altemp = new ArrayList<QuoteItem>();
							altemp.add(qtitem);
							configs.put(qtitem.getBundleId(), altemp);
							if("config".equalsIgnoreCase(qtitem.getLineTypeCd())){
								alstandalones.add(qtitem);
							}
						}
					} else {
						// stand-alone items
						if (cloudProdLine.equalsIgnoreCase(qtitem.getProductLine())) {
							cloudOnes.add(qtitem);
						} else {
							if(Constants.NgqConstants.INDIRECT.equalsIgnoreCase(quote.getGoToMarketRoute()) && (autoBundlePLSet.contains("ALL")
									|| autoBundlePLSet.contains(qtitem.getProductLine()))) {
								autoBundledItems.add(qtitem);
							}
							else{
								if (qtitem.getQuantity() != null && qtitem.getQuantity() != 0) {
									alstandalones.add(qtitem);
								}
							}

						}
					}
				}
			}
		}

		// AtomicInteger docIdx = new AtomicInteger(0);

		//if quote version is 1 send lastDocNum
//		if (employee.getEmailAddress() != null) {
//			String resellerCheck = employee.getEmailAddress();
//			if (resellerCheck.contains("hpe.com") || resellerCheck.contains("hp.com")) {
//			} else {
//				employee = new EmployeeType();
//			}
//		}
		
		List<DealProductDetail> arrayOfLineItemPNType = new ArrayList<>();
		arrayOfLineItemPNType.addAll(Arrays.asList(getStandAloneLineItems(alstandalones,employee, authStatCD, authDate, requestAction,
				lineItemMap, lastPricedDate, dealRegApplied, quote.getDealType(), quote.getAssetQuoteNr(),quote.getGoToMarketRoute()
				,quote.getOrigAsset(),quote.getAdditionalInfo().getDealScenario(),configs)));

		s4Dealrequest.setDealProductDetails(arrayOfLineItemPNType);
		
		
		List<DealDetail> arrayOfLineItemBDType = new ArrayList<>();
		arrayOfLineItemBDType.addAll(Arrays.asList(getBundles(configs,employee, authStatCD, authDate, requestAction, lineItemMap,
				lastPricedDate, quote, dealRegApplied, s4Dealrequest, quote.getDealType(), autoBundledItems)));
		s4Dealrequest.setDealDetails(arrayOfLineItemBDType);
		return s4Dealrequest;

	}
	
	private boolean checkIfDealRegShouldBeApplied(Quote quote, String requestAction,
			boolean isWin, boolean dealRegApplied) {

		if ( Constants.NgqConstants.INDIRECT.equalsIgnoreCase(quote.getGoToMarketRoute())
				&& !(quote.getAdditionalInfo().isOpgExtensionRequested()
						|| quote.getAdditionalInfo().isQuoteExtensionRequested())) {
			dealRegApplied = fetchTreeDataForApplyDealReg(quote, requestAction, isWin);
		}
		return dealRegApplied;
	}

	private Map<String, JSONObject> getEDMSOverrideMappingData(String regionCd, String countryCd, String subCategory,
			String masterData) {

		HashMap<String, JSONObject> resultMap = new HashMap<String, JSONObject>();
		String team = null, userGroup = null;
		Map<String, String> splFields = null, splFields1 = null;
		UserProfile userProfile = CommonUtil.getCurrentUserPreference();

		try {
			team = userProfile.getGroupList().get(0).getTeamNm();
			userGroup = userProfile.getGroupList().get(0).getGroupName();
		} catch (Exception e) {
			LOG.error("Error while geting Team/Group name of User");
		}
		splFields1 = new HashMap<String, String>();
		splFields1.put(MasterDataConstants.specialMappingDataMdmMap.get(MasterDataConstants.CATEGORY_DEAL_TYPE).get(0).get(1),masterData);
		String value = null;
		if (subCategory != null) {
			Set<LookupConfig> dealTypeOther = dropDownListService.getDealType(new String[] { regionCd, countryCd, team, userGroup }, splFields1,DecisionTreeANDFilter.getInstance(), subCategory, "EN");
			if (dealTypeOther != null && !dealTypeOther.isEmpty()) {
				for (LookupConfig lookupConfig : dealTypeOther) {
					if (lookupConfig.getKey().equalsIgnoreCase(masterData)) {
						value = "" + lookupConfig.getLcId();
						break;
					}
				}
			}
		}
		if (value != null) {
			splFields = new HashMap<String, String>();
			splFields.put(MasterDataConstants.specialMappingDataMdmMap.get(MasterDataConstants.CATEGORY_EDMS_OVERRIDE_MAPPING).get(0).get(1), value);
		}
		Set<LookupMappingExt> edmsOverideMapping = dropDownListService.getEDMSOverideMapping(
				new String[] { regionCd, countryCd, team, userGroup }, subCategory, splFields,
				DecisionTreeORFilter.getInstance());
		if (edmsOverideMapping != null && !edmsOverideMapping.isEmpty()) {
			LookupMappingExt edmsOverideMappingData = edmsOverideMapping.iterator().next();
			JSONObject edmsOverideMappingDataObj = new JSONObject();
			edmsOverideMappingDataObj.put("mccOverrideEDMS", edmsOverideMappingData.getExtVal1());
			edmsOverideMappingDataObj.put("bizmodelOverrideEDMS", edmsOverideMappingData.getExtVal2());
			edmsOverideMappingDataObj.put("routing_indicator", edmsOverideMappingData.getExtVal3());
			resultMap.put("edmsOverideMapping", edmsOverideMappingDataObj);

		}
		return resultMap;
	}

	private Calendar getCalendar(Date date, String dateName) {
		if (date == null) {
			LOG.debug("ngqEDMServiceImpl getCalendar " + dateName + " is empty. Defaulting to todays date.");
			date = new Date();
		}
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return calendar;
	}

	private void addIndirectRequestDetails(S4DealCreationRequest s4Dealrequest, Quote quote, EISFormData eisObj) {

		boolean isEisDealCreation = false;
		// for EIS deal
		if (eisObj != null) {
			isEisDealCreation = true;
		}

		if (s4Dealrequest != null) {
			if (quote.getFlags().get(QtFlagType.PARTNEREXClUSIVITY) != null) {
				String partnerExclusivityFlag = quote.getFlags().get(QtFlagType.PARTNEREXClUSIVITY).getFlgVl();
				if ("true".equalsIgnoreCase(partnerExclusivityFlag)) {
					s4Dealrequest.setExclusivePartnerIndicator("Y");
				} else {
					s4Dealrequest.setExclusivePartnerIndicator("N");
				}
			}
			String corpRslrAsDistFl = "N";
			if (quote.getFlags() != null && quote.getFlags().get(QtFlagType.CORPRSLRASDISTI) != null) {

				if ("true".equalsIgnoreCase(quote.getFlags().get(QtFlagType.CORPRSLRASDISTI).getFlgVl())) {
					corpRslrAsDistFl = "Y";
				}
			}
			s4Dealrequest.setCorporateResellerIndicator(corpRslrAsDistFl);
		}

		// reseller information
		// QuoteCustomer reseller =
		// quote.getCustomers().get(CustomerType.RESELLER);
		// QuoteCustomerContact resellerContact =
		// quote.getCustomerContacts().get(CustomerType.RESELLER);
		// //Add the reseller information
		// if (reseller != null) {
		// ResellerDetail[] resellers = new ResellerDetail[1];
		// ResellerDetail resellerB = new ResellerDetail();
		// if (isEisDealCreation &&
		// StringUtils.isNotEmpty(eisObj.getResellerId())) {
		// resellerB.setPartnerProfileIdentifier(eisObj.getResellerId());
		// } else {
		// resellerB.setPartnerProfileIdentifier(reseller.getPartnerId());
		// }
		// if (resellerContact != null) {
		// resellerB.setContactNumber(resellerContact.getPhone());
		// resellerB.setContactEmailAddress(resellerContact.getEmail());
		// resellerB.setContactFirstName(resellerContact.getFirstName());
		// resellerB.setContactLastName(resellerContact.getLastName());
		// }
		// resellers[0] = resellerB;
		// s4Dealrequest.setETY_OM_CREATE_DEAL_RESELLBSet(Arrays.asList(resellers));
		// }

		// Add the reseller information
		if (quote.getResellers() != null && quote.getResellers().entrySet() != null) {
			ResellerDetail[] resellers = new ResellerDetail[quote.getResellers().size()];
			Set<Entry<String, QuoteCustomer>> rslr = quote.getResellers().entrySet();
			int index = 0;

			QuoteCustomerContact resellerContact = null;
			if (isEisDealCreation && StringUtils.isNotEmpty(eisObj.getResellerId())) {
				String eisResellers = eisObj.getResellerId();
				String rslrAry[] = eisResellers.split(",");
				for (int i = 0; i < rslrAry.length; i++) {
					ResellerDetail resellerB = new ResellerDetail();
					resellerB.setPartnerProfileIdentifier(rslrAry[i]);
					if (quote.getResellerContacts() != null && quote.getResellerContacts().size() > 0)
						resellerContact = quote.getResellerContacts().get(rslrAry[i]);

					if (resellerContact != null) {
						resellerB.setContactNumber(resellerContact.getPhone());
						resellerB.setContactEmailAddress(resellerContact.getEmail());
						resellerB.setContactFirstName(resellerContact.getFirstName());
						resellerB.setContactLastName(resellerContact.getLastName());
						resellerB.setReceiveIndicator("N");
						resellerB.setPartyIdentifier("1234");
					}
					resellers[index++] = resellerB;
				}
			} else {
				for (Entry<String, QuoteCustomer> temp : rslr) {
					ResellerDetail resellerB = new ResellerDetail();
					QuoteCustomer reseller = temp.getValue();
					resellerB.setPartnerProfileIdentifier(reseller.getPartnerId());
					if (quote.getResellerContacts() != null && quote.getResellerContacts().size() > 0)
						resellerContact = quote.getResellerContacts().get(reseller.getPartnerId());
					if (resellerContact != null) {
						resellerB.setContactNumber(resellerContact.getPhone());
						resellerB.setContactEmailAddress(resellerContact.getEmail());
						resellerB.setContactFirstName(resellerContact.getFirstName());
						resellerB.setContactLastName(resellerContact.getLastName());
						resellerB.setReceiveIndicator("N");
						resellerB.setPartyIdentifier("1234");
					}
					resellers[index++] = resellerB;
				}

			}
			List<ResellerDetail> arrayOfResellerBType = new ArrayList<>();
			arrayOfResellerBType.addAll(Arrays.asList(resellers));
			s4Dealrequest.setResellerBDetails(arrayOfResellerBType);
		}

		// for EIS deal
		if (isEisDealCreation) {
			s4Dealrequest.setMiscellaneousChargeCode(eisObj.getMcCode());
			if (quote.getFlags().get(QtFlagType.CIPPGMQUOTE) != null
					&& quote.getFlags().get(QtFlagType.CIPPGMQUOTE).getFlgVl() != null
					&& quote.getFlags().get(QtFlagType.CIPPGMQUOTE).getFlgVl().equals("true")) {
//#N/A				dealHeader.setBusModelCD(Constants.NgqConstants.CIP_PROGRAM_QUOTE);
			} else {
//#N/A				dealHeader.setBusModelCD(eisObj.getBussModel());
			}
		}

		// Add the distributor information
		if ("true".equalsIgnoreCase(quote.getAdditionalInfo().getSingleDistributorFlag())) {
			String contractId = null;
			QuoteUtil quoteUtil = new QuoteUtil();
			String distiID = quoteUtil.getDistiID(quote);
			Map<String, QuoteCustomer> distributors = quote.getDistributors();
			QuoteCustomer distributor = distributors.get(distiID);
			if (isEisDealCreation) {
				contractId = eisObj.getContractId();
			}
			setDistrubutorsInfo(contractId, quote.getPriceGeo(), distributor, s4Dealrequest);

		} else {
			Map<String, QuoteCustomer> allDistributors = quote.getDistributors();
			ResellerDetail[] distributors = new ResellerDetail[allDistributors.size()];
			int count = 0;
			for (Map.Entry<String, QuoteCustomer> entry : allDistributors.entrySet()) {
				ResellerDetail resellerA = new ResellerDetail();
				String distributorId = entry.getKey();
				QuoteCustomer distributor = entry.getValue();
				if (distributor != null) {
					if (isEisDealCreation && StringUtils.equals(eisObj.getDistributorId(), distributorId)) {
//#N/A						resellerA.setPrimaryRslFl(YesNoFlagType.Y);
					}
					resellerA.setPartnerProfileIdentifier(distributor.getPartnerId());
//#N/A					resellerA.setPriceGeoCD(quote.getPriceGeo());
					resellerA.setPurchaseAgreementIdentifier(distributor.getContractId());
					resellerA.setContactFirstName("");
					resellerA.setContactLastName("");
					resellerA.setContactNumber("");
					resellerA.setPartyName("");
					resellerA.setCustomerApplicationCode("");
					resellerA.setReceiveIndicator("N");
					distributors[count] = resellerA;
				}
				count++;
			}
			
			List<ResellerDetail> arrayOfResellerAType = new ArrayList<>();
			arrayOfResellerAType.addAll(Arrays.asList(distributors));
			s4Dealrequest.setResellerADetails(arrayOfResellerAType);
		}

		// Below is the test data added by Pavan June 20th
		// Will be removed once the actual data comes in from UI
		// The reseller array count is based on the number of resellers we get
		// from
		// source
		// START TEST DATA
		//
		// * ResellerAType[] resellerAs = new ResellerAType[1]; ResellerAType
		// resellerA = new
		// * ResellerAType(); resellerA.setPartnerProID("3-LT9X8L");
		// * resellerA.setPriceGeoCD("US"); resellerAs[0] = resellerA;
		// * dealRequest.setResellerAs(resellerAs);
		//
		//
		//
		// * ResellerBType[] resellerBs = new ResellerBType[1]; ResellerBType
		// resellerB = new
		// * ResellerBType(); resellerBs[0] = resellerB;
		// *
		// * resellerB.setPartnerProID("3-K9S4VJ");
		// * resellerB.setRslrBEmail("avnet.com@example.com");
		// * resellerB.setRslrBFirstName("Validation");
		// * resellerB.setRslrBLastName("US_RES_C_01");
		// *
		// * resellerBs[0] = resellerB; dealRequest.setResellerBs(resellerBs);

		// END TEST DATA

		// Set the deal Registration list
		List<DealRegistration> dealRegistrationList = quote.getDealRegistrationLst();
		if (dealRegistrationList != null) {
//			DealRegBenefitProgramType[] dealRegBenefitPrograms = new DealRegBenefitProgramType[dealRegistrationList
//					.size()];
//			for (int i = 0; i < dealRegistrationList.size(); i++) {
//				DealRegistration dealRegistration = dealRegistrationList.get(i);
//				DealRegBenefitProgramType dealRegBenefitProgram = new DealRegBenefitProgramType();
//				dealRegBenefitProgram.setDealRegExpirationDate(dateToXMLGregorianCalendar(
//						dealRegistration.getDealRegExpDate().toGregorianCalendar().getTime()));// verify
//				dealRegBenefitProgram.setDealRegID(dealRegistration.getDealRegId());
//				dealRegBenefitProgram.setDealRegPrcType("E"); // Check what his
//																// value maps to
//				dealRegBenefitProgram.setDealRegProgramName(dealRegistration.getProgramId());// Verify
//				dealRegBenefitProgram.setDealRegStatus(dealRegistration.getDealRegStatus());
//				dealRegBenefitPrograms[i] = dealRegBenefitProgram;
//			}
//
//			ArrayOfDealRegBenefitProgramType arrayOfDealRegBenefitProgramType = new ArrayOfDealRegBenefitProgramType();
//			arrayOfDealRegBenefitProgramType.getDealRegBenefitProgram().addAll(Arrays.asList(dealRegBenefitPrograms));
//
//			dealRequest.setDealRegBenefitPrograms(arrayOfDealRegBenefitProgramType);
			s4Dealrequest.setDealRegistrationIdentifier("TRUE");
		}

	}
	
	private void setDistrubutorsInfo(String contractId, String priceGeo, QuoteCustomer distributor,S4DealCreationRequest s4Dealrequest) {

		ResellerDetail[] distributorsLcl = new ResellerDetail[1];
		ResellerDetail resellerA = new ResellerDetail();
		if (distributor != null) {
			resellerA.setPartyIdentifier(distributor.getPartnerId());
			resellerA.setPartyIdentifier("1234");
			resellerA.setReceiveIndicator("N");
			if (contractId != null) {
				resellerA.setPurchaseAgreementIdentifier(contractId);
			}
			distributorsLcl[0] = resellerA;

			s4Dealrequest.setResellerADetails(Arrays.asList(distributorsLcl));
		}

	}
	
	@Override
	public boolean fetchTreeDataForApplyDealReg(Quote quote, String requestAction, boolean isWin) {

		String dealRequestAction = null;
		boolean retValue = false;
		if (isWin) {
			if (quote.getAdditionalInfo().getAcceptPrice() !=null) {
				dealRequestAction = "LOW TOUCH"; // Low touch win deal)
			} else {
				dealRequestAction = "HIGH TOUCH";
			}
		}
		if(dealRequestAction !=null){
			retValue = applyDrCategorySettings(quote, dealRequestAction);
		}

		return retValue;

	}
	
	@Override
	public boolean applyDrCategorySettings(Quote quote, String dealRequestAction){
		QuoteCustomerAddress soldToAddr1 = quote.getCustomerAddresses().get(CustomerType.SOLDTO);
		String region = soldToAddr1.getRegion();
		String country = quote.getCountryCode();
		Map<String, String> splFields = new HashMap<String, String>();
		splFields.put(
				MasterDataConstants.specialMappingDataMdmMap.get(MasterDataConstants.CATEGORY_APPLY_DR).get(0).get(1),
				quote.getBusinessGroup());
		splFields.put(
				MasterDataConstants.specialMappingDataMdmMap.get(MasterDataConstants.CATEGORY_APPLY_DR).get(1).get(1),
				dealRequestAction);
		Set<LookupConfig> applyDealReg = dropDownListService.getApplyDealReg(new String[] { region, country}, "EN", splFields,DecisionTreeORFilter.getInstance(), quote.getGoToMarketRoute());
		if (applyDealReg != null && applyDealReg.size() > 0) {
			LookupConfig temp = applyDealReg.iterator().next();
			return Constants.yesNoMap.get(temp.getValue());

		}
		return false;
	}
	
	private void addSIRequestDetails(S4DealCreationRequest s4Dealrequest, Quote quote,EISFormData eisObj) {

		Map<String, JSONObject> map = 	getEDMSOverrideMappingData(quote.getCustomerAddresses().get(CustomerType.SOLDTO).getRegion(), quote.getCustomerAddresses().get(CustomerType.SOLDTO).getCountry(), quote.getGoToMarketRoute(),quote.getDealType());
		JSONObject edmsOverideMappingDataObj = new JSONObject();
		edmsOverideMappingDataObj = map.get("edmsOverideMapping");
		if(!map.isEmpty()){
			try{
				String mccOverrideEDMS = (String) edmsOverideMappingDataObj.get("mccOverrideEDMS");
		        String bizmodelOverrideEDMS = (String) edmsOverideMappingDataObj.get("bizmodelOverrideEDMS");
		        String routingIndicator = (String) edmsOverideMappingDataObj.get("routing_indicator");
		        s4Dealrequest.setMiscellaneousChargeCode(mccOverrideEDMS);
//#N/A				dealHeader.setBusModelCD(bizmodelOverrideEDMS);
				s4Dealrequest.setRoutingIndicator(routingIndicator);
			}
			catch(Exception e){
				throw (new TypeMismatchDataAccessException("Mcc Override EDMS and Biz model Override EDMS values is not present for deal type "+quote.getDealType()+" for region "+ quote.getCustomerAddresses().get(CustomerType.SOLDTO).getRegion()));
			}

			List<OptimusEclipseMapping> listEclipseMapping = dropDownListService.loadOptimusEclipseMapping();
			boolean checkbg = false;
			boolean checkbu = false;

			Iterator<OptimusEclipseMapping> itEclipseMapping = listEclipseMapping.iterator();
			while (itEclipseMapping.hasNext()) {
				OptimusEclipseMapping optimusEclipseMapping = (OptimusEclipseMapping) itEclipseMapping.next();
				if(optimusEclipseMapping.getOptimusCode().equalsIgnoreCase(quote.getAdditionalInfo().getBusinessGrpForSI())&&optimusEclipseMapping.getBusinessType().equalsIgnoreCase("BG")){
					
					s4Dealrequest.setLeadBusinessAreaGroupCode(optimusEclipseMapping.getEclipseCode());
					checkbg = true;
				}
				else{
					if(optimusEclipseMapping.getOptimusCode().equalsIgnoreCase(quote.getAdditionalInfo().getBusinessUnit())&&optimusEclipseMapping.getBusinessType().equalsIgnoreCase("BU")){
						s4Dealrequest.setLeadBusinessUnitCode(optimusEclipseMapping.getEclipseCode());
						checkbu = true;
					}
				}
			}
			if(checkbg==false){
				throw (new TypeMismatchDataAccessException("BusGroup value is not present for "+quote.getAdditionalInfo().getBusinessGrpForSI()));
			}
			if(checkbu==false)
			{
				throw (new TypeMismatchDataAccessException("BusUnit value is not present for "+quote.getAdditionalInfo().getBusinessUnit()));
			}
		}
		if(quote.getGoToMarketRoute().equalsIgnoreCase("SI"))
		{
			if (quote.getResellers() != null && quote.getResellers().entrySet()!=null) {
				ResellerDetail[] resellers = new ResellerDetail[quote.getResellers().size()];
				ResellerDetail[] resellersA = new ResellerDetail[quote.getResellers().size()];
				Set<Entry<String, QuoteCustomer>> rslr = quote.getResellers().entrySet();
				int index=0;
				boolean isEisDealCreation = false;
				if (eisObj != null) {
					isEisDealCreation = true;
				}
				for(Entry<String, QuoteCustomer> temp :rslr){
					ResellerDetail resellerB = new ResellerDetail();
					ResellerDetail resellerA = new ResellerDetail();
					QuoteCustomer reseller = temp.getValue();
					String distributorId = temp.getKey();
					if (isEisDealCreation && StringUtils.isNotEmpty(eisObj.getResellerId())) {
						resellerB.setPartnerProfileIdentifier(eisObj.getResellerId());
						resellerA.setPartnerProfileIdentifier(eisObj.getResellerId());
					} else {
						resellerB.setPartnerProfileIdentifier(reseller.getPartnerId());
						resellerA.setPartnerProfileIdentifier(reseller.getPartnerId());
					}
					QuoteCustomerContact resellerContact = null;
					if(quote.getResellerContacts()!=null && quote.getResellerContacts().size()>0)
						resellerContact = quote.getResellerContacts().get(reseller.getPartnerId());
					if (resellerContact != null) {
						resellerB.setContactNumber(resellerContact.getPhone());
						resellerB.setContactEmailAddress(resellerContact.getEmail());
						resellerB.setContactFirstName(resellerContact.getFirstName());
						resellerB.setContactLastName(resellerContact.getLastName());
						resellerB.setReceiveIndicator("N");
						resellerB.setPartyIdentifier("1234");

						resellerA.setContactNumber(resellerContact.getPhone());
						resellerA.setContactEmailAddress(resellerContact.getEmail());
						resellerA.setContactFirstName(resellerContact.getFirstName());
						resellerA.setContactLastName(resellerContact.getLastName());
						resellerA.setReceiveIndicator("N");
						resellerA.setPartyIdentifier("1234");

						if (isEisDealCreation && StringUtils.equals(eisObj.getDistributorId(), distributorId)) {
//#N/A							resellerA.setPrimaryRslFl(YesNoFlagType.Y);
						}
//#N/A						resellerA.setPriceGeoCD(quote.getPriceGeo());
						resellerA.setPurchaseAgreementIdentifier(resellerContact.getContactId());
					}
					resellers[index] = resellerB;
					resellersA[index++] = resellerA;
				}
				List<ResellerDetail> arrayOfResellerBType = new ArrayList<>();
				arrayOfResellerBType.addAll(Arrays.asList(resellers));
				
				List<ResellerDetail> arrayOfResellerAType = new ArrayList<>();
				arrayOfResellerBType.addAll(Arrays.asList(resellersA));
				
				s4Dealrequest.setResellerBDetails(arrayOfResellerBType);
				s4Dealrequest.setResellerADetails(arrayOfResellerAType);
			}
		}


	}
	
	private boolean isNotHighRiskDeal(Quote quote) {

		boolean isnotHighriskDeal = true;
		if (quote.getFlags().get(QtFlagType.HIGHRISKDEALFLAG) != null
				&& StringUtils.equals("true", quote.getFlags().get(QtFlagType.HIGHRISKDEALFLAG).getFlgVl()))
			isnotHighriskDeal = false;

		return isnotHighriskDeal;
	}
	
	public void exeEmailInThread(final Quote quote, final String scenario) {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		final UserProfile up = CommonUtil.getCurrentUserPreference();
		final KibanaData kibana = CommonUtil.getKibanaData();
		executorService.execute(new Runnable() {
			public void run() {
				try {
					NGQThreadLocal.setUserProfile(up);
					NGQThreadLocal.setKibanaData(kibana);
					emailService.configureEmail(quote, scenario);
				} finally {
					NGQThreadLocal.removeUserProfile();
					NGQThreadLocal.removeKibanaData();
				}
			}
		});
		executorService.shutdown();
	}
	
	public static XMLGregorianCalendar dateToXMLGregorianCalendar(Date date) {
	    XMLGregorianCalendar xmlGregorianCalendar = null;
	    GregorianCalendar gregorianCalendar = new GregorianCalendar();
	    gregorianCalendar.setTime(date);
	    try {
	      DatatypeFactory dataTypeFactory = DatatypeFactory.newInstance();
	      xmlGregorianCalendar = dataTypeFactory.newXMLGregorianCalendar(gregorianCalendar);
	    }
	    catch (Exception e) {
	      System.out.println("Exception in conversion of Date to XMLGregorianCalendar" + e);
	    }

	    return xmlGregorianCalendar;
	}
	
//	private DealHPUserType getDealHPUser(String setDealUserTypeCD, EmployeeType employee) {
//		DealHPUserType dealHPUser = new DealHPUserType();
//		// dealHPUser.setDealUserDescription(dealUserDescription);
//		dealHPUser.setDealUserTypeCD(setDealUserTypeCD);
//		dealHPUser.setHPEmployee(employee);
//
//		return dealHPUser;
//	}
	
	public Date evaluateQuoteEndDate(Quote quote, String requestAction, Boolean isWin, Calendar cal, Calendar opgCal,
			Date quoteExpiryTs) {
		int opgValidity = 0;
		int quoteValidity = 0;
		boolean dealRegExists = false;
		String dealRegValidity = "";
		int euvValidity = 0;
		boolean euvExtension = false;
		
		String subCategory = quote.getGoToMarketRoute();
        if(quote.getOriginatingReferenceAsset() != null){
      	  if(quote.getOriginatingReferenceAsset().contains("RESELLER")){
      		  subCategory = "Reseller";
      	  }else if(quote.getOriginatingReferenceAsset().equalsIgnoreCase("DISTRIBUTOR") ){
      		  subCategory = "Distributor";
      	  }
        }
		try {
			Map<String, String> specialFieldsBu = new HashMap<>();
			specialFieldsBu.put(MasterDataConstants.specialMappingDataMdmMap
					.get(MasterDataConstants.CATEGORY_QUOTE_AND_OPG_VALIDITY).get(0).get(1),  quote.getBusinessGroup());

			Set<PartnerQuoteAndOPG> quoteAndOpgValidity = dropDownListService.getQuoteAndOpgValidity(new String[]{ quote.getCustomerAddresses().get(CustomerType.SOLDTO).getRegion(),
					quote.getCustomerAddresses().get(CustomerType.SOLDTO).getCountry(),null,null},
					specialFieldsBu, DecisionTreeANDFilter.getNonStrictInstance(), subCategory, "EN");


			Set<PartnerQuoteAndOPG> dealRegSet = dropDownListService.getDealReg(
					new String[] { quote.getCustomerAddresses().get(CustomerType.SOLDTO).getRegion(),
							quote.getCustomerAddresses().get(CustomerType.SOLDTO).getCountry(), null, null },
					subCategory, "EN");
			Set<LookupMappingExt> euvTimeFrame = dropDownListService.getEUVTimeFrame(
					new String[] { quote.getCustomerAddresses().get(CustomerType.SOLDTO).getRegion(),
							quote.getCustomerAddresses().get(CustomerType.SOLDTO).getCountry(), null, null },
					subCategory, "EN");

			if (euvTimeFrame != null && !euvTimeFrame.isEmpty()) {
				LookupMappingExt lookupMappingExt = euvTimeFrame.iterator().next();
				euvValidity = Integer.valueOf(lookupMappingExt.getExtVal1());

			}
			if (dealRegSet != null && !dealRegSet.isEmpty()) {
				PartnerQuoteAndOPG deal = dealRegSet.iterator().next();
				dealRegValidity = deal.getMasterDescription();
			}
			if(quoteAndOpgValidity !=null && !quoteAndOpgValidity.isEmpty()){
				for(PartnerQuoteAndOPG partnerQuoteAndOPG :quoteAndOpgValidity){
					Map<String, String> specialFieldMap = partnerQuoteAndOPG.getSpecialFieldMap();
					if (specialFieldMap != null && specialFieldMap.containsKey("busgrp") && specialFieldMap.get("busgrp").equals(quote.getBusinessGroup())) {
						quoteValidity =Integer.valueOf(partnerQuoteAndOPG.getQuote());
						opgValidity = Integer.valueOf(partnerQuoteAndOPG.getOpg());
						break;
					}else {
						partnerQuoteAndOPG = quoteAndOpgValidity.iterator().next();
						quoteValidity = Integer.valueOf(partnerQuoteAndOPG.getQuote());
						opgValidity = Integer.valueOf(partnerQuoteAndOPG.getOpg());
					}
				}
			}
		} catch (Exception e) {
			LOG.error("exception in getting quote and opg validity", e);
			e.printStackTrace();
		}
		// TODO please refactor below condition
		// check if this condition is even required?? or take directly from
		// reqestedExtensionTs
		if (quote.getAdditionalInfo().isOpgExtensionRequested()
				|| quote.getAdditionalInfo().isQuoteExtensionRequested()) {
			quoteExpiryTs = quote.getAdditionalInfo().getRequestedExtensionts();
			if (quote.getAdditionalInfo().isOpgExtensionRequested() && quote.getAdditionalInfo().isEuvExtension()) {
				euvExtension = true;
				if (quote.getAdditionalInfo().getCustomOpgValidity() > euvValidity || opgValidity > euvValidity) {
					opgCal.add(Calendar.DATE, (euvValidity == 0) ? opgValidity : euvValidity);
					// quoteExpiryTs = cal.getTime();
				} else if (quote.getAdditionalInfo().getCustomOpgValidity() < euvValidity
						|| opgValidity < euvValidity) {
					if (quote.getAdditionalInfo().getRequestedExtensionts() != null) {
						quoteExpiryTs = quote.getAdditionalInfo().getRequestedExtensionts();
					} else {
						if (quote.getAdditionalInfo().getCustomOpgValidity() > 0) {
							opgCal.add(Calendar.DATE, quote.getAdditionalInfo().getCustomOpgValidity());
						} else {
							opgCal.add(Calendar.DATE, opgValidity);
						}
					}
				}
			} else if (quote.getAdditionalInfo().isOpgExtensionRequested()
					&& !quote.getAdditionalInfo().isEuvExtension()) {
				if (quote.getAdditionalInfo().getCustomOpgValidity() < euvValidity || opgValidity < euvValidity) {
					euvExtension = true;
					if (quote.getAdditionalInfo().getRequestedExtensionts() != null) {
						euvSubmitRequired = false;
						quoteExpiryTs = quote.getAdditionalInfo().getRequestedExtensionts();
					} else {
						if (quote.getAdditionalInfo().getCustomOpgValidity() > 0) {
							cal.add(Calendar.DATE, quote.getAdditionalInfo().getCustomOpgValidity());
						} else {
							cal.add(Calendar.DATE, opgValidity);
						}
					}
				}
			}
		} else if (quote.getAdditionalInfo().isOPGCreatedOnce() || isWin) {

			if (StringUtils.isNotBlank(quote.getAdditionalInfo().getEuvEmail())) {
				Date rightnow = new Date();
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(rightnow);

				if (quote.getAdditionalInfo().getOpgCreatedDateForHighRisk() == null) {
					quote.getAdditionalInfo().setOpgCreatedDateForHighRisk(calendar.getTime());
				}
				if(quote.getAdditionalInfo().isEuvExtension()){
				euvExtension = true;
				}
				if (quote.getAdditionalInfo().getCustomOpgValidity() > euvValidity || opgValidity > euvValidity) {
					cal.add(Calendar.DATE, (euvValidity == 0) ? opgValidity : euvValidity);
				} else if (quote.getAdditionalInfo().getCustomOpgValidity() <= euvValidity
						|| opgValidity <= euvValidity) {
					if (quote.getAdditionalInfo().getRequestedExtensionts() != null) {
						quote.setExpiryTs(quote.getAdditionalInfo().getRequestedExtensionts());
						quoteExpiryTs = quote.getAdditionalInfo().getRequestedExtensionts();
					} else {
						if (quote.getAdditionalInfo().getCustomOpgValidity() > 0) {
							cal.add(Calendar.DATE, quote.getAdditionalInfo().getCustomOpgValidity());
						} else {
							cal.add(Calendar.DATE, opgValidity);
						}
					}

				}
			} else {
				if (quote.getAdditionalInfo().getCustomOpgValidity() > 0) {
					cal.add(Calendar.DATE, quote.getAdditionalInfo().getCustomOpgValidity());
				} else {
					cal.add(Calendar.DATE, opgValidity);
				}
			}

			if (dealRegValidity != null) {
				String[] dealRegValidityTied = dealRegValidity.split("-");
				if (dealRegValidityTied != null && dealRegValidityTied.length > 0
						&& "Y".equalsIgnoreCase(dealRegValidityTied[0])
						&& quote.getAdditionalInfo().getMinimumDealRegExpiryts() != null) {
					dealRegExists = true;
				}
			}
		} else {
			if (quote.getAdditionalInfo().getValidityPeriod() > 0) {
				cal.add(Calendar.DATE, quote.getAdditionalInfo().getValidityPeriod());
			} else {
				cal.add(Calendar.DATE, quoteValidity);
			}
		}
		if (!(quote.getAdditionalInfo().isOpgExtensionRequested()
				|| quote.getAdditionalInfo().isQuoteExtensionRequested())) {
			quote.setExpiryTs(cal.getTime());
			quoteExpiryTs = cal.getTime();
		} else {
			if (StringUtils.startsWithIgnoreCase(requestAction, "LOW")
					&& quoteExpiryTs.compareTo(quote.getAdditionalInfo().getRequestedExtensionts()) == 0) {
				quote.setExpiryTs(quote.getAdditionalInfo().getRequestedExtensionts());
			}
		}

		if (dealRegExists) {
			Date minDealRegOrEuv = quote.getAdditionalInfo().getMinimumDealRegExpiryts();
			if (euvExtension) {
				if (quoteExpiryTs.before(quote.getAdditionalInfo().getMinimumDealRegExpiryts())) {
					minDealRegOrEuv = quoteExpiryTs;
				}
			}
			quote.setExpiryTs(minDealRegOrEuv);
			quoteExpiryTs = minDealRegOrEuv;
		}
		return quoteExpiryTs;
	}
	
	private String getEmployee(QuoteContact quoteContact, String quoteContactType, String createdBy) {

		String employee = "";

		if (quoteContact != null) {
			LOG.debug("ngqEDMServiceImpl.contact " + quoteContactType + " email : " + quoteContact.getEmail());
			employee = quoteContact.getEmail();
			
			 
		} else {
			LOG.debug("ngqEDMServiceImpl.contact " + quoteContactType + " is null. CreatedBy email : " + createdBy);
			employee = createdBy;
		}
		employee = filterNonHpeEmail(employee);
		return employee;
	}
	
	private String filterNonHpeEmail(String emailId) {
		String emailDomain = emailId.split("@")[1].trim();
		if (!("hpe.com".equalsIgnoreCase(emailDomain) || "hp.com".equalsIgnoreCase(emailDomain))) {
			emailId = "";
		}
		return emailId;
	}
	
	private void addComment(S4DealCreationRequest s4Dealrequest, Quote quote, String comment,boolean isExternal) {

//		List<DealCommentType> dealComments = new ArrayList<>();

//		if(dealRequest.getDealComments()!=null)
//			dealComments = dealRequest.getDealComments().getDealComment();

		if (StringUtils.isNotEmpty(comment)) {
//			DealCommentType dealComment = new DealCommentType();
			if (isExternal) {
//				dealComment.setAddedByHPEmployee(employee);
				s4Dealrequest.setCommentSetting("Y");
			} else {
//				dealComment.setAddedByHPEmployee(employee);
				s4Dealrequest.setCommentSetting("N");
			}
//			s4Dealrequest.setCommentDate((getCalendar(new Date(), "CommentDate").getTime()));
			s4Dealrequest.setCommentDate(null);
			// dealComment.setCommentGroupCD(commentGroupCD);
			
			s4Dealrequest.setComments(comment);

//			if (dealComments != null) {
//				List<DealCommentType> dealCommentsList = dealComments;
//				dealCommentsList.add(dealComment);
//				dealComments = dealCommentsList;
//			} else {
//				dealComments = Arrays.asList(new DealCommentType[1]);
//				dealComments.add(0,dealComment);
//			}
//			ArrayOfDealCommentType arrayOfDealCommentType=new ArrayOfDealCommentType();
//			arrayOfDealCommentType.getDealComment().addAll(dealComments);
//			dealRequest.setDealComments(arrayOfDealCommentType);
		}
	}
	
	private void addOtherComment(S4DealCreationRequest s4Dealrequest, Quote quote, String comment,boolean isExtComment, String commentTypCd) {
//		List<DealCommentType> dealComments = dealRequest.getDealComments().getDealComment();

		if (StringUtils.isNotEmpty(comment)) {
//			DealCommentType dealComment = new DealCommentType();
			if (isExtComment) {
//				dealComment.setAddedByHPEmployee(employee);
				s4Dealrequest.setCommentSetting("Y");
			} else {
				s4Dealrequest.setCommentSetting("N");
			}
//			s4Dealrequest.setCommentDate((getCalendar(new Date(), "CommentDate").getTime()));
			s4Dealrequest.setCommentDate(null);
			// dealComment.setCommentGroupCD(commentGroupCD);
			if (commentTypCd != null) {
				s4Dealrequest.setCommentType(commentTypCd);
			}
			s4Dealrequest.setComments(comment);

//			if (dealComments != null) {
//				List<DealCommentType> dealCommentsList = dealComments;
//				dealCommentsList.add(dealComment);
//				dealComments = dealCommentsList;
//			} else {
//				dealComments = Arrays.asList(new DealCommentType[1]);
//				dealComments.add(0,dealComment);
//			}

//			ArrayOfDealCommentType arrayOfDealCommentType=new ArrayOfDealCommentType();
//			arrayOfDealCommentType.getDealComment().addAll(dealComments);

//			dealRequest.setDealComments(arrayOfDealCommentType);
		}
	}
	
	private void addDistiChangeComment(S4DealCreationRequest s4Dealrequest, Quote quote,String comment) {
//		List<DealCommentType> dealComments = dealRequest.getDealComments().getDealComment();

		if (StringUtils.isNotEmpty(comment)) {
//			DealCommentType dealComment = new DealCommentType();
			if (!quote.getGoToMarketRoute().equalsIgnoreCase("Indirect")) {
//				dealComment.setAddedByHPEmployee(employee);
				s4Dealrequest.setCommentSetting("Y");
			} else {
				s4Dealrequest.setCommentSetting("N");
			}
//			s4Dealrequest.setCommentDate((getCalendar(new Date(), "CommentDate").getTime()));
			s4Dealrequest.setCommentDate(null);
			// dealComment.setCommentGroupCD(commentGroupCD);
			s4Dealrequest.setCommentType("DistiChange");
			s4Dealrequest.setComments(comment);

//			if (dealComments != null) {
//				List<DealCommentType> dealCommentsList = dealComments;
//				dealCommentsList.add(dealComment);
//				dealComments = dealCommentsList;
//			} else {
//				dealComments = Arrays.asList(new DealCommentType[1]);
//				dealComments.add(0,dealComment);
//			}
//
//			ArrayOfDealCommentType arrayOfDealCommentType=new ArrayOfDealCommentType();
//			arrayOfDealCommentType.getDealComment().addAll(dealComments);

//			dealRequest.setDealComments(arrayOfDealCommentType);
		}
	}
	
	private void addIndirectCommentDetails(S4DealCreationRequest s4Dealrequest, Quote quote) {

//		List<DealCommentType> dealComments;
//		if(dealRequest.getDealComments()!=null)
//		{
//			dealComments = dealRequest.getDealComments().getDealComment();
//		}else
//		{
//			dealComments=new ArrayList<DealCommentType>();
//		}

//		DealCommentType dealComment = new DealCommentType();
		// dealComment.setAddedByHPEmployee(employee);
//		s4Dealrequest.setCommentDate((getCalendar(new Date(), "CommentDate").getTime()));
		s4Dealrequest.setCommentDate(null);
		// dealComment.setCommentGroupCD(commentGroupCD);
		s4Dealrequest.setCommentType("ES");
		s4Dealrequest.setCommentSetting("N");

		if (StringUtils.equalsIgnoreCase(quote.getGoToMarketRoute(), "Indirect")) {
			if(StringUtils.startsWith(quote.getAdditionalInfo().getAutoEscalationScenario(), "Dealtype")){
				s4Dealrequest.setComments(quote.getAdditionalInfo().getAutoEscalationScenario());
			}else{
				s4Dealrequest.setComments(quote.getAdditionalInfo().getAutoEscalationReason());
			}
		} else {
			s4Dealrequest.setComments(quote.getAdditionalInfo().getAutoEscalationScenario());
		}
//		if (dealComments != null) {
//			List<DealCommentType> dealCommentsList = dealComments;
//			dealCommentsList.add(dealComment);
//			dealComments = dealCommentsList;
//		} else {
//			dealComments = Arrays.asList(new DealCommentType[1]);
//			dealComments.add(0,dealComment);
//		}
//
//		ArrayOfDealCommentType arrayOfDealCommentType=new ArrayOfDealCommentType();
//		arrayOfDealCommentType.getDealComment().addAll(dealComments);
//
//		dealRequest.setDealComments(arrayOfDealCommentType);
	}
	
	private Set<String> getAutoBundleCheck(Quote quote) {
		String subCategory = quote.getGoToMarketRoute();
		Set<String> autoBundlePLList = new HashSet<String>();
		if (quote.getOriginatingReferenceAsset() != null) {
			if (quote.getOriginatingReferenceAsset().contains("RESELLER")) {
				subCategory = "Reseller";
			} else if (quote.getOriginatingReferenceAsset().equalsIgnoreCase("DISTRIBUTOR")) {
				subCategory = "Distributor";
			}
		}
		Set<LookupMappingExt> autoBundlePL = dropDownListService.getAutoBundle(
				new String[] { quote.getCustomerAddresses().get(CustomerType.SOLDTO).getRegion(),
						quote.getCustomerAddresses().get(CustomerType.SOLDTO).getCountry(), null, null },
				subCategory, "EN");


		if (autoBundlePL != null && !autoBundlePL.isEmpty()) {
			LookupMappingExt lookupMappingExt = autoBundlePL.iterator().next();
			if(lookupMappingExt != null && lookupMappingExt.getExtVal1() !=null){
				autoBundlePLList.add(lookupMappingExt.getExtVal1().toUpperCase());
			}
		}

		return autoBundlePLList;
	}
	
	private void setBundledIdforEcomm(QuoteItem qtitem, Map<String, String> configdmapping) {
		String configId;
		configId = configdmapping.get(qtitem.getCnfgnSystemName());
		if (configId == null && qtitem.getIconId() != null && !qtitem.getIconId().equals("")) {
			configId = qtitem.getBundleId();
			configdmapping.put(qtitem.getIconId(), configId);
		}
		if (configId == null && StringUtils.isNotEmpty(qtitem.getBundleId())) {
			configId = qtitem.getBundleId();
			configdmapping.put(qtitem.getCnfgnSystemName(), configId);
		}
		if (configId == null) {
			configId = qtitem.getCnfgnSysId();
			if (StringUtils.isNotEmpty(configId) && configId.length() > 8) {
				configId = configId.substring(configId.length() - 8);
			}
			configdmapping.put(qtitem.getCnfgnSystemName(), configId);
		}
		qtitem.setBundleId(configId);
	}
	
	private DealProductDetail[] getStandAloneLineItems(ArrayList<QuoteItem> standalones, String employee,
			String authStatCD, Calendar authDate, String requestAction, HashMap<String, Integer> lineItemMap,
			Calendar lastPricedDate, boolean dealRegApplied, String dealType, String quoteNumber,
			String routeToMarket, String ngqAcountType,String dealScenario,LinkedHashMap<String, ArrayList<QuoteItem>> configs) {
		LOG.debug("ngqEDMServiceImpl.getStandAloneLineItems : " + standalones.size());
		DealProductDetail[] lineItemsPN = new DealProductDetail[standalones.size()];

		int idx = 0;
		for (QuoteItem lineItem : standalones) {
			DealProductDetail lineItemPN = new DealProductDetail();

			LOG.debug("ngqEDMServiceImpl.idx : " + idx);
			LOG.debug("ngqEDMServiceImpl.lineItem.getProductNr() : " + lineItem.getProductNr());
			LOG.debug("ngqEDMServiceImpl.lineItem.getProductOption() : " + lineItem.getProductOption());
			LOG.debug("ngqEDMServiceImpl.lineItem.getLineTypeCd() : " + lineItem.getLineTypeCd());
			LOG.debug("ngqEDMServiceImpl.lineItem.getProductDescription() : " + lineItem.getProductDescription());
			LOG.debug("ngqEDMServiceImpl.lineItem.getQuantity().longValue() : " + lineItem.getQuantity().longValue());
			LOG.debug("ngqEDMServiceImpl.lineItem.getAdditionalInfo().getBndlTyp() : "+ lineItem.getAdditionalInfo().getBndlTyp());
			LOG.debug("ngqEDMServiceImpl.lineItem.getAdditionalInfo().getBndDl() : "+ lineItem.getAdditionalInfo().getBndDl());
			LOG.debug("ngqEDMServiceImpl.lineItem.getAdditionalInfo().getBndVrn() : "+ lineItem.getAdditionalInfo().getBndVrn());
			LOG.debug("ngqEDMServiceImpl.lineItem.getAdditionalInfo().getCfgId() : "+ lineItem.getAdditionalInfo().getCfgId());

//NF			if (lineItem.isEccBundle()) {
//				lineItemPN.setEccBundle(YesNoFlagType.Y);
//			} else {
//				lineItemPN.setEccBundle(YesNoFlagType.N);
//			}

//			if (lineItem.getAddToCatalog()) {
//				lineItemPN.setAddToCatalog(YesNoFlagType.Y);
//			} else {
//				lineItemPN.setAddToCatalog(YesNoFlagType.N);
//			}
//			if (lineItem.getFlags().get(QtItemFlagType.HASMCC) != null && lineItem.getFlags().get(QtItemFlagType.HASMCC).getFlgVl() != null && lineItem.getFlags().get(QtItemFlagType.HASMCC).getFlgVl().equals("true")) {
//				  lineItemPN.setHasMCC(YesNoFlagType.Y);
//				}
//				else {
//					lineItemPN.setHasMCC(YesNoFlagType.N);
//				}

			if (lineItem.getLclUntLstAmt() == null) {
				lineItem.setLclUntLstAmt(BigDecimal.ZERO);
			}
//#N/A			lineItemPN.setAddedByHPEmployee(employee);
			lineItemPN.setUserEmailID(employee);
			// lineItemPN.setAuthBDNetAmt(authBDNetAmt);

			lineItemPN.setAuthorizationBasisText(authBasisDesc);
			if (authDate != null) {
				String authDate1 = dateToXMLGregorianCalendar(authDate.getTime()).toString();
				if(authDate1 != null)
				lineItemPN.setAuthorizedDate(authDate1.substring(0, authDate1.length()-1));
			}
//			lineItemPN.setAuthHPEmployee(employee);
			lineItemPN.setAuthorizedProductStatusCode(authStatCD);
			if (lineItem.getAdditionalInfo().getMedallionPrice() != null)
				lineItemPN.setMedallionPercentage(lineItem.getAdditionalInfo().getMedallionPrice().abs().toString());
			lineItemPN.setMedallionLevelCode(lineItem.getAdditionalInfo().getMedallionType());
			if(lineItem.getAdditionalInfo().getInstantPriceAmt() != null)
				lineItemPN.setInstantPricingAmount(String.valueOf(lineItem.getAdditionalInfo().getInstantPriceAmt()));
			else
				lineItemPN.setInstantPricingAmount("0");
			lineItemPN.setInstantPricingMethodAmount(lineItem.getAdditionalInfo().getPricingMethod());

			// lineItemPN.setBDNetAmt(getBDNetAmt(lineItem.getLclUntLstAmt(),
			// lineItem.getDiscountAmount(), lineItem.getDiscountTypeCd(),
			// lineItem.getDiscountPercent(), requestAction));
			if (lineItem.getQuantity() != null && lineItem.getQuantity() == 0) {
				lineItemPN.setAuthorizedBigDealNetAmount(null);
			} else {
				BigDecimal bdNet = getBDNetAmt(lineItem.getLclUntLstAmt(), lineItem.getRqstAmount(),
						lineItem.getRqstDiscountType(), lineItem.getDiscountPercent(), requestAction,
						lineItem.getLclUntNtAmt(), dealType);
				if (Constants.NgqConstants.INDIRECT.equalsIgnoreCase(routeToMarket)) {
					bdNet = alterBDNet(bdNet, lineItem, dealRegApplied);
				}
				lineItemPN.setAuthorizedBigDealNetAmount(String.valueOf(bdNet));
			}
			applyLineItemAuth(lineItemPN, lineItem, authDate, lineItemPN.getAuthorizedBigDealNetAmount(), requestAction, quoteNumber,
					routeToMarket);
			if(ngqAcountType.equals(Constants.NgqConstants.NGQ_PARTNER) && lineItem.getTotalCost() != null)
				lineItemPN.setDealItemCostAmount(lineItem.getTotalCost().toString());
			else
				lineItemPN.setDealItemCostAmount(BigDecimal.ZERO.toString());
//			lineItemPN.setLineItemNumber(lineItem.getAdditionalInfo().getItmSqn() == 0 ? Integer.valueOf(lineItem.getSlsQtnItmSqnNr())
//						: lineItem.getAdditionalInfo().getItmSqn());
			lineItemPN.setLineItemNumber(String.valueOf(lineItem.getAdditionalInfo().getItmSqn() == 0 ? Integer.valueOf(lineItem.getSlsQtnItmSqnNr())
					: lineItem.getAdditionalInfo().getItmSqn()));
			if (lineItem.isPrivateSku()) {
				lineItemPN.setFutureProductIndicator("Y");
			} else {
				lineItemPN.setFutureProductIndicator("N");
			}
			if (lineItem.getFlags().get(QtItemFlagType.ISFUTUREPRODUCT) != null
					&& lineItem.getFlags().get(QtItemFlagType.ISFUTUREPRODUCT).getFlgVl() != null
					&& lineItem.getFlags().get(QtItemFlagType.ISFUTUREPRODUCT).getFlgVl().equals("true")) {
				lineItemPN.setFutureProductIndicator("Y");
//#N/A				lineItemPN.setUseExternalListPriceFl(YesNoFlagType.Y);
			} else {
				lineItemPN.setFutureProductIndicator("N");
			}

			lineItemMap.put(lineItem.getSlsQtnItmSqnNr(), Integer.valueOf(lineItemPN.getLineItemNumber()));
			LOG.debug("lineItemMap.put : " + lineItem.getSlsQtnItmSqnNr() + ":" + lineItemPN.getLineItemNumber());

//#N/A			lineItemPN.setGuidanceAvailFl(YesNoFlagType.N);
			if (lineItem.getAdditionalInfo().getTypicalGd() != null) {
//				lineItemPN.setGuidanceAvailFl(YesNoFlagType.Y);
				if (lineItem.getAdditionalInfo().getGdId() != null) {
					Long guidanceDetailsID = new Long(lineItem.getAdditionalInfo().getGdId());
					lineItemPN.setGuidanceDetailID(String.valueOf(guidanceDetailsID));
				}else{
					lineItemPN.setGuidanceDetailID("0");
				}
				if (lineItem.getAdditionalInfo().getExpertGd() != null) {
					lineItemPN.setExpertAddPercentage(String.valueOf(lineItem.getAdditionalInfo().getExpertGd().abs()));
				}else{
					lineItemPN.setExpertAddPercentage("");
				}
				if (lineItem.getAdditionalInfo().getFloorGd() != null) {
					lineItemPN.setFloorAddPercentage(String.valueOf(lineItem.getAdditionalInfo().getFloorGd().abs()));
				}else{
					lineItemPN.setFloorAddPercentage("");
				}
				if (lineItem.getAdditionalInfo().getTypicalGd() != null) {
					lineItemPN.setTypicalAddPercentage(String.valueOf(lineItem.getAdditionalInfo().getTypicalGd().abs()));
				}else{
					lineItemPN.setTypicalAddPercentage("");
				}
				if(lastPricedDate != null){
					String guidanceRfrsDate = dateToXMLGregorianCalendar(lastPricedDate.getTime()).toString();
					if(guidanceRfrsDate != null)
						lineItemPN.setGuidanceRefreshDate(guidanceRfrsDate.substring(0, guidanceRfrsDate.length()-1));
				}
			}

			lineItemPN.setListPriceAmount(String.valueOf(lineItem.getLclUntLstAmt()));
//			lineItemPN.setPL(lineItem.getProductLine());
			lineItemPN.setProductLineCode(lineItem.getProductLine());
			lineItemPN.setPriceTypeCode("B");
			
			/*if(lineItem.getProductNr() != null)
			lineItemPN.setMaterialIdentifier(lineItem.getProductNr().substring(0, 7));*/
			
			// Vignesh - When product number length is not greater than 7 throws StringIndexOutOfBoundsException
			lineItemPN.setMaterialIdentifier(lineItem.getProductNr());
//			lineItemPN.setMaterialDescription(StringUtils.substring(lineItem.getProductDescription(), 0, 65));
			if(lineItem.getProductDescription() != null)
			lineItemPN.setMaterialDescription(lineItem.getProductDescription());
			lineItemPN.setSourceSystemQuantity(String.valueOf(lineItem.getQuantity()));
			lineItemPN.setRequestType("ADD");
			dealRegAtLineItem(dealRegApplied, lineItem);
			// ys - add PA for standalone product
			// ys - fix PA is negative in eclipse issue
			if (lineItem.getPaDiscountPercent() != null) {
				if (dealType != null && StringUtils.equalsIgnoreCase(Constants.DealType.TACTICAL_GIVEAWAY, dealType)) {
					lineItemPN.setStandardDiscountPercentage(null);
				} else {
					lineItemPN.setStandardDiscountPercentage(String.valueOf(lineItem.getPaDiscountPercent().abs()));
				}
			}
			// TODO should not be needed
			// lineItemPN.setRolloutMonthQtys("1");
			lineItemPN.setOptionCode(lineItem.getProductOption());
			lineItemPN.setLineaddedbyEmployeeNumber("0");
			if (lineItem.getProgramBenefitValues() != null
					&& lineItem.getProgramBenefitValues().getProgramBenefits() != null) {
//#N/A				lineItemPN.setCurrencyProgramBenefit(lineItem.getProgramBenefitValues().getProgramBenefits());
			}
			//--------
			lineItemPN.setExtendedListPriceAmount("");
			lineItemPN.setDealValueDiscountAmount("");
			lineItemPN.setMedallionLevelCode("");
			lineItemPN.setExtendedEstimatedKAmount("");
			if(lineItem.getUcid() != null && "config".equalsIgnoreCase(lineItem.getLineTypeCd())){
				lineItemPN.setLineTypeCode("BD");
				//Pass the Material Identifier as Product Number of its first component if it is a Config Header
				ArrayList<QuoteItem> wholeConfig = configs.get(lineItem.getBundleId());
				QuoteItem firstComponent = wholeConfig.get(1);
				lineItemPN.setMaterialIdentifier(firstComponent.getProductNr());
				lineItemPN.setSourceConfigurationIdentifier(lineItem.getUcid());
				if(lineItem.getIconId() != null){
					lineItemPN.setIconID(lineItem.getIconId());
				}
			}
			else{
				lineItemPN.setLineTypeCode("PN");
			}
			lineItemPN.setNonDiscountableIndicator("X");
			lineItemPN.setSpecialTermsText("TRUE");
			//------
			lineItemsPN[idx++] = lineItemPN;
		}
		LOG.debug("ngqEDMServiceImpl.getStandAloneLineItems : end");

		return lineItemsPN;
	}
	
	private void applyLineItemAuth(DealProductDetail lineItemPN, QuoteItem lineItem, Calendar authDate,String bdNetPrice, String requestAction, String quoteNumber, String routeToMarket) {
		String authBasisDesc = getAuthBasisDesc(lineItem, new BigDecimal(bdNetPrice), routeToMarket);
		if (requestAction.equals("LOW_TOUCH_VERSION_ADD")) {
			if (StringUtils.isNotEmpty(authBasisDesc)) {
				authBasisDesc = authBasisDesc + " from " + quoteNumber;
			} else {
				authBasisDesc = quoteNumber;
			}
		}
		if (StringUtils.isNotEmpty(authBasisDesc)) {
			if (authDate == null) {
				authDate = getCalendar(new Date(), "authDate");
				String authDate1 = dateToXMLGregorianCalendar(authDate.getTime()).toString();
				if(authDate1 != null)
				lineItemPN.setAuthorizedDate(authDate1.substring(0, authDate1.length()-1));
			}
			lineItemPN.setAuthorizedProductStatusCode("Y");
			lineItemPN.setAuthorizationStatus("Y");
			lineItemPN.setAuthorizationBasisText(authBasisDesc);
		}
	}
	
	private String getAuthBasisDesc(QuoteItem lineItem, BigDecimal bdNetPrice, String routeToMarket) {
		String authBasisDesc = null;

		if (lineItem.getLclUntLstAmt() == null || bdNetPrice == null) {
			return authBasisDesc;
		}

		if ((lineItem.getAdditionalInfo().getPrcDispCd() == 1) && (StringUtils.isNotEmpty(lineItem.getDealNr()))) {
			authBasisDesc = lineItem.getDealNr();
			return authBasisDesc;
		}

		BigDecimal discountAmt = (lineItem.getLclUntLstAmt().subtract(bdNetPrice));
		if (discountAmt.compareTo(BigDecimal.ZERO) == 0 || discountAmt.compareTo(BigDecimal.ZERO) == -1) {
			return authBasisDesc;
		}

		BigDecimal discount = BigDecimal.ZERO;
		if (lineItem.getDiscountPercent() != null) {
			discount = lineItem.getDiscountPercent().abs();
		}

		BigDecimal paDiscount = BigDecimal.ZERO;
		if (lineItem.getPaDiscountPercent() != null) {
			paDiscount = lineItem.getPaDiscountPercent().abs();
		}

		BigDecimal dealDiscount = BigDecimal.ZERO;
		if (lineItem.getDealDiscountPercent() != null) {
			dealDiscount = lineItem.getDealDiscountPercent().abs();
		}

		BigDecimal rqstDiscount = BigDecimal.ZERO;
		if (StringUtils.isNotEmpty(lineItem.getRqstDisPct())) {
			rqstDiscount = new BigDecimal(lineItem.getRqstDisPct());
		}

		if ("Indirect".equalsIgnoreCase(routeToMarket)) {
			if ((StringUtils.isEmpty(lineItem.getRqstDisPct())
					|| BigDecimal.ZERO.compareTo(new BigDecimal(lineItem.getRqstDisPct())) == 0)
					&& paDiscount.compareTo(discount) != 0) {// TODO check deal discount as well??
				return "paNr";
			}
		}

		if (rqstDiscount.compareTo(BigDecimal.ZERO) == 1 && rqstDiscount.compareTo(paDiscount) == 1) {
			return authBasisDesc;
		}

		if (paDiscount.compareTo(BigDecimal.ZERO) == 1 && dealDiscount.compareTo(BigDecimal.ZERO) == 1) {
			// PA, Deal
			if (paDiscount.add(dealDiscount).compareTo(discount) == 0) {
				if (paDiscount.compareTo(dealDiscount) == 1) {
					// PA is higher
					authBasisDesc = lineItem.getPaNr();
				} else {
					// Deal is higher
					authBasisDesc = lineItem.getDealNr();
				}
			}
		} else if (paDiscount.compareTo(BigDecimal.ZERO) == 1 && paDiscount.compareTo(discount) == 0) {
			authBasisDesc = lineItem.getPaNr();
		} else if (dealDiscount.compareTo(BigDecimal.ZERO) == 1 && dealDiscount.compareTo(discount) == 0) {
			authBasisDesc = lineItem.getDealNr();
		}

		return authBasisDesc;
	}
	
	private void dealRegAtLineItem(boolean dealRegApplied, QuoteItem lineItem) {
		if (dealRegApplied) {
			if (lineItem.getFlags() != null && lineItem.getFlags().get(QtItemFlagType.DEALREGAPPLIEDLINE) != null
					&& lineItem.getFlags().get(QtItemFlagType.DEALREGAPPLIEDLINE).getFlgVl() != null && (("true")
							.equalsIgnoreCase(lineItem.getFlags().get(QtItemFlagType.DEALREGAPPLIEDLINE).getFlgVl()) ||
							("N/A")
							.equalsIgnoreCase(lineItem.getFlags().get(QtItemFlagType.DEALREGAPPLIEDLINE).getFlgVl()))) {
				lineItem.setDealRegApplied(false);
			} else {
				lineItem.setDealRegApplied(true);
			}
		}
	}
	
	private DealDetail[] getBundles(HashMap<String, ArrayList<QuoteItem>> configs, String employee,
			String authStatCD, Calendar authDate, String requestAction, HashMap<String, Integer> lineItemMap,
			Calendar lastPricedDate, Quote quote, boolean dealRegApplied, S4DealCreationRequest s4Dealrequest, String dealType, ArrayList<QuoteItem> autoBundleItems)

	{
		Integer autoBundleDflt = 99999;
		// array of bundles
		LOG.debug("ngqEDMServiceImpl.getBundles : " + configs.size());
		DealDetail[] lineItemsBD =null;
		if(autoBundleItems.size() > 0){
			 lineItemsBD = new DealDetail[configs.size() + 1];
		}else{
			 lineItemsBD = new DealDetail[configs.size()];
		}

		int bundleIdx = 0;
		Long bndlHeaderQty = 0L;
		String bundleHeaderDesc ="";
		
//		 * 315820 CR - [NGQChannel Nov Release][WW][Regression]- At Orderable Status,
//		 * when the end date needs t be changed in both NGQ and Eclipse, Eclipse handles
//		 * single lines and bundles in a different way 11/12/2017 bundle header qty
//		 * should be the qty of the config header. Noticed that in some cases the config
//		 * header is not even sent and in other cases it is.. Freshly added config
//		 * doesnt send config header. Cloned quote or new versioned does. Regardless the
//		 * correct header needs to be computed.
		 

		for (String arg : configs.keySet()) {
			// for each config in quote
			ArrayList<QuoteItem> configProductList = configs.get(arg);

			DealDetail lineItemBD = new DealDetail();

			QuoteItem bundleHeader = configProductList.get(0);
			QuoteItem firstProdOfConfig = null;
//#N/A			lineItemBD.setMedallionNetAmount(bundleHeader.getAdditionalInfo().getMedallionPrice() != null
//					? bundleHeader.getAdditionalInfo().getMedallionPrice().abs().toString()
//					: BigDecimal.ZERO.toString());
			String bndlSrc = bundleHeader.getConfigBandingType();

			String bundleDesc = null;
			// calculating bundleDesc for eclipse
			if (("Bundle").equals(bundleHeader.getLineTypeCd())) {
				bundleDesc = bundleHeader.getCnfgnSysId();
			} else if (("Config").equals(bundleHeader.getLineTypeCd())) {
				if (bundleHeader.getIconId() != null && !bundleHeader.getIconId().equals("")) {
//NF					lineItemBD.setIconID(bundleHeader.getIconId());
				}
				bundleDesc = bundleHeader.getUcid();
			}

			if (bundleHeader.isEccBundle()) {
//NF				lineItemBD.setEccBundle(YesNoFlagType.Y);
			} else {
//NF				lineItemBD.setEccBundle(YesNoFlagType.N);
			}

			if (bundleHeader.getAddToCatalog()) {
//				lineItemBD.setAddToCatalog(YesNoFlagType.Y);
			} else {
//				lineItemBD.setAddToCatalog(YesNoFlagType.N);
			}

			// Fix for CR 312573. 1st index of the config is being set as the config header
			for (QuoteItem configLineItem : configProductList) {
				// get the first real product of the configProductList as the bundle header
				// skip if first item in list is a comment
				if(quote.getOrigAsset().equals(Constants.NgqConstants.NGQ_PARTNER) && configLineItem.getTotalCost() != null)
					lineItemBD.setDealItemCostAmount(configLineItem.getTotalCost().toString());
				else
					lineItemBD.setDealItemCostAmount(BigDecimal.ZERO.toString());
				if (configLineItem.getProductNr() == null || configLineItem.getQuantity() == null
						|| configLineItem.displayOnly || ("Config").equals(configLineItem.getLineTypeCd())
						|| ("Bundle").equals(configLineItem.getLineTypeCd())) {
					bndlHeaderQty = configLineItem.getAdditionalInfo().getUiQuantity();
					bundleHeaderDesc = configLineItem.getProductDescription();
					continue;
				} else {
					firstProdOfConfig = configLineItem;
					break;
				}
			}
			// Fix for IM Ticket 3885305 : List price changes when first component quantity
			// is more than one.
			// Because the Config header quantity is set as first component quantity

			// bundleHeader.setQuantity(bndlHeaderQty);

			LOG.debug("ngqEDMServiceImpl.bundle : " + arg + " bundleIdx " + bundleIdx);
			LOG.debug("ngqEDMServiceImpl.bundleHeader.getProductNr() : " + bundleHeader.getProductNr());
			LOG.debug("ngqEDMServiceImpl.bundleHeader.getQuantity() : " + bundleHeader.getQuantity());
			LOG.debug("ngqEDMServiceImpl.bundleHeader.getProductDescription() : " + bundleHeader.getProductDescription());
			LOG.debug("ngqEDMServiceImpl.bundleHeader.getLclUntLstAmt() : " + bundleHeader.getLclUntLstAmt());
			LOG.debug("ngqEDMServiceImpl.bundleHeader.getProductLine() : " + bundleHeader.getProductLine());
			LOG.debug("ngqEDMServiceImpl.bundleHeader.getPaDiscountPercent() : " + bundleHeader.getPaDiscountPercent());
			LOG.debug("ngqEDMServiceImpl.bundleHeader.getDiscountTypeCd() : " + bundleHeader.getDiscountTypeCd());
			LOG.debug("ngqEDMServiceImpl.bundleHeader.getDiscountAmount() : " + bundleHeader.getDiscountAmount());

			// for DisplayCompPrcFl
			if (StringUtils.equals("Indirect", quote.getGoToMarketRoute())) {
				String dispCode = quote.getAdditionalInfo().getShowConfigPrice();
				QuoteCustomerAddress soldToAddr1 = quote.getCustomerAddresses().get(CustomerType.SOLDTO);
				QuoteCustomer soldTo = quote.getCustomers().get(CustomerType.SOLDTO);
				String region = soldToAddr1.getRegion();
				String country = quote.getCountryCode();

				dispCode= dropDownListService.getConfigPriceVisibility(new String[]{region,country}, Constants.NgqConstants.DISTRIBUTOR, "EN", soldTo.getCustSegCd());
//#N/A				if (StringUtils.equals(ConfigPriceVisibility.SHOW.getCode(), dispCode)) {
//					lineItemBD.setDisplayCompPrcFl(YesNoFlagType.Y);
//				} else if (StringUtils.equals(ConfigPriceVisibility.HIDE.getCode(), dispCode)) {
//					lineItemBD.setDisplayCompPrcFl(YesNoFlagType.N);
//				}
			}

			if (bundleHeader.getLclUntLstAmt() == null) {
				firstProdOfConfig.setLclUntLstAmt(BigDecimal.ZERO);
			}
//#N/A			lineItemBD.setAddedByHPEmployee(employee);
//			lineItemBD.setAuthBasisDesc(authBasisDesc);
			if (authDate != null) {
				String authDate1 = dateToXMLGregorianCalendar(authDate.getTime()).toString();
				if(authDate1 != null){
//					lineItemBD.setAuthorizedDate(authDate1.substring(0, authDate1.length()-1));
					lineItemBD.setSourceSystemUpdateDate(authDate1.substring(0, authDate1.length()-1));
				}
//				lineItemBD.setAuthorizedDate(dateToXMLGregorianCalendar(authDate.getTime()).toString());
//				lineItemBD.setAuthDateGMT(dateToXMLGregorianCalendar(authDate.getTime()));
			}
//			lineItemBD.setAuthHPEmployee(employee);
			lineItemBD.setAuthorizationStatus(authStatCD);
			// Oct 26th 2017 - 312573 - [NGQChannel Sept Release][WW] Discounts incorrect in
			// Low touch NGQ Deals (Mandates refresh PL totals) hence nullifying.
			// lineItemBD.setBDNetAmt(getBDNetAmt(bundleHeader.getLclUntLstAmt(),
			// bundleHeader.getRqstAmount(), bundleHeader.getRqstDiscountType(),
			// bundleHeader.getDiscountPercent(), requestAction,
			// bundleHeader.getLclUntNtAmt()));
//#N/A			lineItemBD.setBDNetAmt(null);
//			lineItemBD.setCostPrice(BigDecimal.ZERO);

//			lineItemBD.setLineItemNumber((bundleHeader.getAdditionalInfo().getItmSqn()) == 0
//					? Integer.valueOf(bundleHeader.getSlsQtnItmSqnNr())
//					: (bundleHeader.getAdditionalInfo().getItmSqn()));
			//Pointing to Config Header LineItem
			lineItemBD.setLineItemNumber(String.valueOf((bundleHeader.getAdditionalInfo().getItmSqn()) == 0
					? Integer.valueOf(bundleHeader.getSlsQtnItmSqnNr())
					: (bundleHeader.getAdditionalInfo().getItmSqn())));

			// Oct 26th 2017 - 312573 - [NGQChannel Sept Release][WW] Discounts incorrect in
			// Low touch NGQ Deals (Mandates refresh PL totals) hence nullifying.
			// lineItemBD.setListPrice(bundleHeader.getLclUntLstAmt());
			if (("Direct").equalsIgnoreCase(quote.getGoToMarketRoute())
					/*&& dealHeader.getSuppressListPriceRefreshFl().value().equalsIgnoreCase("Y")*/) {
//				lineItemBD.setListPrice(bundleHeader.getLclUntLstAmt());
			} else {
//				lineItemBD.setListPrice(null);
			}
//			lineItemBD.setProdDesc(bundleHeaderDesc);
//			lineItemBD.setPL(bundleHeader.getProductLine());
			// B = BDnet and P = Percent
//			lineItemBD.setPricingTypeCD("B");
			// lineItemBD.setProdDesc(prodDesc);
			// Qty for config header to be set to one if multiqty configs are used in NGQ..

			// Fix for IM Ticket 3885305 : List price changes when first component quantity
			// is more than one.
			// Because the Config header quantity is set as first component quantity
//			lineItemBD.setQty(bndlHeaderQty);
//			lineItemBD.setRequestType(LineItemRequestTypeFlagType.ADD);

			dealRegAtLineItem(dealRegApplied, firstProdOfConfig);
			// TODO should not be needed
			// lineItemBD.setRolloutMonthQtys("1");

			// ys fix PA is negative in eclipse issue
			if (firstProdOfConfig.getPaDiscountPercent() != null) {
				// Oct 26th 2017 - 312573 - [NGQChannel Sept Release][WW] Discounts incorrect in
				// Low touch NGQ Deals (Mandates refresh PL totals) hence nullifying.
				// lineItemBD.setStdDiscPct(bundleHeader.getPaDiscountPercent().abs());
//				lineItemBD.setStdDiscPct(null);
			}
			// lineItemBD.setBundleDesc(bundleHeader.getProductDescription());
			// lineItemBD.setBundleDesc(arg);
			// as args is now has cofogID we are getting UCid/configsysid as desc
			lineItemBD.setBundleDescription(bundleDesc);
//			lineItemBD.setBundleIndex(Integer.valueOf(bundleIdx));
			// for eCommerce quote pass ConfigBandingType from corona
			if (quote.getCatalogMaintenanceQuote() && StringUtils.isNotBlank(bndlSrc)) {
				lineItemBD.setBundleSource(bndlSrc);
			} else { // oterwise pass fixed value
				lineItemBD.setBundleSource(bundleSource);
			}
//			lineItemBD.setBundleIdentifier(arg);
			lineItemBD.setBundleIdentifier(bundleHeader.getUcid());
			lineItemBD.setMaterialDescription(bundleHeader.getProductDescription());
//			lineItemBD.setBundleIdentifier(arg.substring(0, 8));
			List<QuoteItem> listItem = new ArrayList<QuoteItem>();

			for (QuoteItem configLineItem : configProductList) {
				if (("Product").equalsIgnoreCase(configLineItem.getLineTypeCd())
						|| "Option".equalsIgnoreCase(configLineItem.getLineTypeCd())) {
					// if (!configLineItem.isPrivateSku())
					listItem.add(configLineItem);
				} else if (("Bundle").equals(configLineItem.getLineTypeCd())) {
					lineItemMap.put(configLineItem.getSlsQtnItmSqnNr(), Integer.valueOf(lineItemBD.getLineItemNumber()));
					LOG.debug("lineItemMap.put bundle : " + configLineItem.getProductNr() + ":"
							+ configLineItem.getSlsQtnItmSqnNr() + ":" + lineItemBD.getLineItemNumber());
				}
			}

			// lineItemBD.setProdBaseNr(bundleHeader.getProductNr());
//			lineItemBD.setProdBaseNr(listItem.get(0).getProductNr());

			// array of line items in a bundle
			DealBundleDetail[] bundleLines = new DealBundleDetail[listItem.size()];

			int idx = 0;
			for (QuoteItem configLineItem : listItem) {
				// for each lineItem in a config
				if (configLineItem.getProductNr() == null || configLineItem.getQuantity() == null
						|| configLineItem.displayOnly || ("Config").equals(configLineItem.getLineTypeCd())
						|| ("Bundle").equals(configLineItem.getLineTypeCd())) {
					continue;
				}

				LOG.debug("ngqEDMServiceImpl.bundle : " + arg + " bundleIdx " + bundleIdx + " idx " + idx);
				LOG.debug("ngqEDMServiceImpl.configLineItem.getProductNr() : " + configLineItem.getProductNr());
				LOG.debug("ngqEDMServiceImpl.configLineItem.getProductOption() : " + configLineItem.getProductOption());
				LOG.debug("ngqEDMServiceImpl.configLineItem.getQuantity() : " + configLineItem.getQuantity());
				LOG.debug("ngqEDMServiceImpl.configLineItem.getProductDescription() : "
						+ configLineItem.getProductDescription());
				LOG.debug("ngqEDMServiceImpl.configLineItem.getOmsLineNr() : " + configLineItem.getOmsLineNr());
				LOG.debug("ngqEDMServiceImpl.configLineItem.getLclUntLstAmt() : " + configLineItem.getLclUntLstAmt());
				LOG.debug("ngqEDMServiceImpl.configLineItem.getProductLine() : " + configLineItem.getProductLine());
				LOG.debug("ngqEDMServiceImpl.configLineItem.getPaDiscountPercent() : "
						+ configLineItem.getPaDiscountPercent());

				DealBundleDetail bundleLine = new DealBundleDetail();
				addBundleLineDetails(bundleLine,configLineItem, authDate,employee,
						authStatCD, requestAction, quote, dealRegApplied,bundleHeader);
				// sce/oca sequence number
				if (configLineItem.getCnfgnLineItemSeq() != null) {
//					bundleLine.setExtLineItemNr(Integer.valueOf(configLineItem.getCnfgnLineItemSeq()));
				}

				addGuidanceDetails(bundleLine, configLineItem, lastPricedDate);

//				bundleLine.setIconID(configLineItem.getIconId());
				 //CR # 333834 is fixed
				//bundleLine.setIconConfigID(configLineItem.getIconName());

				additionalBundleLineDetails(bundleLine, configLineItem, dealType, dealRegApplied);

				// should be set to parent docNum
//#N/A				bundleLine.setParentIndex(bundleIdx);
				lineItemMap.put(configLineItem.getSlsQtnItmSqnNr(), Integer.valueOf(lineItemBD.getLineItemNumber()));
				bundleLines[idx++] = bundleLine;
			}

			// add array of lineItems to bundle
			List<DealBundleDetail> arrayOfBundleLineType = new ArrayList<>();
			arrayOfBundleLineType.addAll(Arrays.asList(bundleLines));
			lineItemBD.setDealBundleDetails(arrayOfBundleLineType);
			
			// add bundle to array of bundles
			lineItemsBD[bundleIdx] = lineItemBD;

			bundleIdx++;
		}

		if(autoBundleItems.size() > 0){
			QuoteItem bundleHeader =new QuoteItem();
			for(QuoteItem autoBundleQtItm: autoBundleItems){
				if("Config".equalsIgnoreCase(autoBundleQtItm.getLineTypeCd())){
					bundleHeader = autoBundleQtItm;
					break;
				}
			}
			DealDetail lineItemAutoBD = new DealDetail();
//			lineItemAutoBD.setEccBundle(YesNoFlagType.N);
//			lineItemAutoBD.setAddToCatalog(YesNoFlagType.N);
//			lineItemAutoBD.setAddedByHPEmployee(employee);
//			lineItemAutoBD.setAuthBasisDesc(authBasisDesc);
			if (authDate != null) {
				String authDate1 = dateToXMLGregorianCalendar(authDate.getTime()).toString();
				if(authDate1 != null){
//					lineItemAutoBD.setAuthorizedDate(authDate1.substring(0, authDate1.length()-1));
					lineItemAutoBD.setSourceSystemUpdateDate(authDate1.substring(0, authDate1.length()-1));
				}
					
			}

//			lineItemAutoBD.setAuthHPEmployee(employee);
			lineItemAutoBD.setAuthorizationStatus(authStatCD);
//			lineItemAutoBD.setBDNetAmt(null);
			lineItemAutoBD.setDealItemCostAmount(BigDecimal.ZERO.toString());
			lineItemAutoBD.setLineItemNumber(String.valueOf(autoBundleDflt));
//			lineItemAutoBD.setListPrice(null);
//			lineItemAutoBD.setProdDesc(null);
//			lineItemAutoBD.setPL(null);
//			lineItemAutoBD.setPricingTypeCD("B");
//			lineItemAutoBD.setQty(1L);
//			lineItemAutoBD.setRequestType(LineItemRequestTypeFlagType.ADD);
//			lineItemAutoBD.setStdDiscPct(null);
			lineItemAutoBD.setBundleDescription("Auto Bundle Header");
//			lineItemAutoBD.setBundleIndex(autoBundleDflt);
//			lineItemAutoBD.setBundleSource("SOFT");
			lineItemAutoBD.setBundleIdentifier(String.valueOf(autoBundleDflt));
//			lineItemAutoBD.setDisplayCompPrcFl(YesNoFlagType.Y);
			DealBundleDetail[] autobundleLines = new DealBundleDetail[autoBundleItems.size()];
			int idx = 0;
			for(QuoteItem autoBundleQtItm: autoBundleItems){
				DealBundleDetail autobundleLine = new DealBundleDetail();
				addBundleLineDetails(autobundleLine,autoBundleQtItm, authDate,authStatCD,employee, requestAction, quote, dealRegApplied,bundleHeader);
				addGuidanceDetails(autobundleLine, autoBundleQtItm, lastPricedDate);
				additionalBundleLineDetails(autobundleLine, autoBundleQtItm, dealType, dealRegApplied);
//#N/A				autobundleLine.setParentIndex(autoBundleDflt);
				lineItemMap.put(autoBundleQtItm.getSlsQtnItmSqnNr(), autoBundleDflt);
				LOG.debug("lineItemMap.put auto bundle : " + autoBundleQtItm.getProductNr() + ":"
						+ autoBundleQtItm.getSlsQtnItmSqnNr() + ":" + autoBundleDflt);
				autobundleLines[idx++] = autobundleLine;
				if(quote.getOrigAsset().equals(Constants.NgqConstants.NGQ_PARTNER))
					lineItemAutoBD.setDealItemCostAmount(autoBundleQtItm.getTotalCost().toString());
				else
					lineItemAutoBD.setDealItemCostAmount(BigDecimal.ZERO.toString());
			}
			List<DealBundleDetail> arrayOfBundleLineType = new ArrayList<>();
			arrayOfBundleLineType.addAll(Arrays.asList(autobundleLines));

			lineItemAutoBD.setDealBundleDetails(arrayOfBundleLineType);
			lineItemsBD[bundleIdx] = lineItemAutoBD;
			bundleIdx++;
		}
		LOG.debug("ngqEDMServiceImpl.getBundles : end");

		return lineItemsBD;
	}
	
	private void addBundleLineDetails(DealBundleDetail bundleLine, QuoteItem configLineItem, Calendar authDate, String employee,
			String authStatCD, String requestAction, Quote quote, boolean dealRegApplied,QuoteItem bundleHeader){
		if (configLineItem.getLclUntLstAmt() == null) {
			configLineItem.setLclUntLstAmt(BigDecimal.ZERO);
		}

//#N/A				bundleLine.setAdHocFl(YesNoFlagType.N);

//#N/A				bundleLine.setAuthBasisDesc(authBasisDesc);
				if (authDate != null) {
					String authDate1 = dateToXMLGregorianCalendar(authDate.getTime()).toString();
					if(authDate1 != null)
					bundleLine.setAuthorizedDate(authDate1.substring(0, authDate1.length()-1));
//					bundleLine.setAuthDateGMT(dateToXMLGregorianCalendar(authDate.getTime()));
				}
				bundleLine.setAuthorizationStatusDescription(authBasisDesc);
//				bundleLine.setAuthHPEmployee(employee);
				bundleLine.setAuthorizationStatus(authStatCD);
				bundleLine.setProductNumber(configLineItem.getProductNr());
				
//				bundleLine.setLineItemNumber(String.valueOf(configLineItem.getUcLineItemId()));
				bundleLine.setLineItemNumber("");//S4 Requested to send empty string
				//Item Sequence inside config refers to Parent config Header Item Number
				bundleLine.setItemSequencingNumber(String.valueOf((bundleHeader.getAdditionalInfo().getItmSqn()) == 0
						? Integer.valueOf(bundleHeader.getSlsQtnItmSqnNr())
								: (bundleHeader.getAdditionalInfo().getItmSqn())));
				bundleLine.setLineaddedbyEmployeeNumber("0");
				bundleLine.setBundleIdentifier(configLineItem.getUcid());
				bundleLine.setAuthorizerEmailID(employee);
				bundleLine.setAuthorizationStatus(authStatCD);
				if (configLineItem.getAdditionalInfo().getMedallionPrice() != null)
					bundleLine.setMedallionNetAmount(configLineItem.getAdditionalInfo().getMedallionPrice().abs().toString());
				bundleLine.setMedallionLevel(configLineItem.getAdditionalInfo().getMedallionType());
				if(configLineItem.getAdditionalInfo().getInstantPriceAmt() != null)
					bundleLine.setInstantPricingAmount(String.valueOf(configLineItem.getAdditionalInfo().getInstantPriceAmt()));
				else
					bundleLine.setInstantPricingAmount("0");
				bundleLine.setInstantPricingMethod(configLineItem.getAdditionalInfo().getPricingMethod());
				if (configLineItem.getQuantity() != null && configLineItem.getQuantity() == 0) {
					bundleLine.setAuthorizedBigDealNetAmount(null);
				} else {
					BigDecimal bdNet = getBDNetAmt(configLineItem.getLclUntLstAmt(), configLineItem.getRqstAmount(),
							configLineItem.getRqstDiscountType(), configLineItem.getDiscountPercent(), requestAction,
							configLineItem.getLclUntNtAmt(), quote.getDealType());
					if (Constants.NgqConstants.INDIRECT.equalsIgnoreCase(quote.getGoToMarketRoute())) {
						bdNet = alterBDNet(bdNet, configLineItem, dealRegApplied);
					}
					bundleLine.setAuthorizedBigDealNetAmount(String.valueOf(bdNet));
				}
				applyBundleLineItemAuth(bundleLine, configLineItem, authDate, new BigDecimal(bundleLine.getAuthorizedBigDealNetAmount()), requestAction,
						quote.getAssetQuoteNr(), quote.getGoToMarketRoute());

				if (quote.getCatalogMaintenanceQuote()
						&& StringUtils.isNotBlank(configLineItem.getConfigBandingType())) {
					bundleLine.setBundleSource(configLineItem.getConfigBandingType());
				} else { // oterwise pass fixed value
					bundleLine.setBundleSource(bundleSource);
				}
				bundleLine.setProductCost(BigDecimal.ZERO.toString());
				if(quote.getOrigAsset().equals(Constants.NgqConstants.NGQ_PARTNER) && configLineItem.getTotalCost() != null)
					bundleLine.setDealItemCostAmount(configLineItem.getTotalCost().toString());
				else
					bundleLine.setDealItemCostAmount(BigDecimal.ZERO.toString());
//				if (configLineItem.isPrivateSku()) {
//#N/A					bundleLine.setFutureProductFl(YesNoFlagType.Y);
//				} else {
//					bundleLine.setFutureProductFl(YesNoFlagType.N);
//				}
//				if (configLineItem.getFlags().get(QtItemFlagType.ISFUTUREPRODUCT) != null
//						&& configLineItem.getFlags().get(QtItemFlagType.ISFUTUREPRODUCT).getFlgVl() != null
//						&& configLineItem.getFlags().get(QtItemFlagType.ISFUTUREPRODUCT).getFlgVl().equals("true")) {
//					bundleLine.setFutureProductFl(YesNoFlagType.Y);
//#N/A					bundleLine.setUseExternalListPriceFl(YesNoFlagType.Y);
//				} else {
//#N/A					bundleLine.setFutureProductFl(YesNoFlagType.N);
//				}

	}
	
	private BigDecimal getBDNetAmt(BigDecimal listPrice, String discount, String discountType,
			BigDecimal netDiscountPercent, String requestAction, BigDecimal netPrice, String dealType) {
		BigDecimal bdNetAmt = new BigDecimal(0);

		// TODO should this be hardcoded to NET
		discountType = "Net";
		BigDecimal priceDiscount = new BigDecimal(0);
		if (discount != null) {
			// priceDiscount = new BigDecimal(Float.parseFloat(discount));
			priceDiscount = new BigDecimal(new BigDecimal(discount).toPlainString());// new
																						// BigDecimal(discount).toPlainString();
			if (netPrice != null && netPrice.compareTo(priceDiscount) == -1) {
				// CR 313653 - Requested discount % is lower than PA %. The net is then based
				// off of the higher PA discount, ignoring the requested %.
				priceDiscount = netPrice;
			}
		}

		LOG.debug("ngqEDMServiceImpl.getBDNetAmt priceDiscount : " + priceDiscount);
		LOG.debug("ngqEDMServiceImpl.getBDNetAmt netDiscountPercent : " + netDiscountPercent);
		LOG.debug("ngqEDMServiceImpl.getBDNetAmt listPrice : " + listPrice);
		LOG.debug("ngqEDMServiceImpl.getBDNetAmt discount : " + discount);
		LOG.debug("ngqEDMServiceImpl.getBDNetAmt netPrice : " + netPrice);
		LOG.debug("ngqEDMServiceImpl.getBDNetAmt discountType : " + discountType);
		LOG.debug("ngqEDMServiceImpl.getBDNetAmt requestAction : " + requestAction);

		if (requestAction.equals("LOW_TOUCH") || requestAction.equals("LOW_TOUCH_VERSION")
				|| requestAction.equals("LOW_TOUCH_VERSION_ADD")) {
			// bdNetAmt = listPrice.add(listPrice.multiply(netDiscountPercent).divide(new
			// BigDecimal(100), 2, BigDecimal.ROUND_HALF_UP));
			if (priceDiscount.compareTo(BigDecimal.ZERO) > 0) {
				bdNetAmt = priceDiscount;
			} else {
				bdNetAmt = netPrice;
			}
		} else {
			if (listPrice.compareTo(BigDecimal.ZERO) > 0) {
				if (priceDiscount.compareTo(BigDecimal.ZERO) > 0) {
					if (discountType.equals("%")) {
						// discountLine = atof(string(line.discount_line + tradeInLineDiscount ));
						bdNetAmt = listPrice.subtract(listPrice.multiply(priceDiscount).divide(new BigDecimal(100), 2,
								BigDecimal.ROUND_HALF_UP));
					} else if (discountType.equalsIgnoreCase("Amt")) {
						// if(tradeInLineDiscount <> 0.0) {
						// discountLine = discountLine + ((ListPriceLine * tradeInLineDiscount) / 100);
						// }
						bdNetAmt = listPrice.subtract(priceDiscount);
					} else if (discountType.equalsIgnoreCase("Net")) {
						// if(tradeInLineDiscount <> 0.0) {
						// discountLine = discountLine - ((ListPriceLine * tradeInLineDiscount) / 100);
						// }
						bdNetAmt = priceDiscount;
					} else {
						bdNetAmt = listPrice;
					}
				} else {
					// bdNetAmt =
					// listPrice.subtract(listPrice.multiply(netDiscountPercent).divide(new
					// BigDecimal(100), 2, BigDecimal.ROUND_HALF_UP));
					// confirm with bluce, if there is no discount, the netAmt= netPrice;
					bdNetAmt = netPrice;
				}
			} else {
				
//				 * if(CommonUtil.isRegionAPJ(quoteOld) &&
//				 * listPrice.compareTo(BigDecimal.ZERO)==0){ bdNetAmt = netPrice; }
				 
			}
		}
		if (dealType != null && StringUtils.equalsIgnoreCase(Constants.DealType.TACTICAL_GIVEAWAY, dealType)) {
			bdNetAmt = BigDecimal.ZERO;
		}
		LOG.debug("ngqEDMServiceImpl.getBDNetAmt bdNetAmt : " + bdNetAmt);
		return bdNetAmt;
	}
	
	private BigDecimal alterBDNet(BigDecimal bdNet, QuoteItem lineItem, boolean dealRegApplied){
		if((lineItem.getProgramBenefitValues() !=null && lineItem.getProgramBenefitValues().getProgramBenefits() !=null
				 &&  lineItem.getFlags() !=null &&  lineItem.getFlags().get(QtItemFlagType.DEALREGAPPLIEDLINE) !=null &&  lineItem.getFlags().get(QtItemFlagType.DEALREGAPPLIEDLINE).getFlgVl() !=null
				 && ("true").equalsIgnoreCase(lineItem.getFlags().get(QtItemFlagType.DEALREGAPPLIEDLINE).getFlgVl()) ) || dealRegApplied){
			bdNet= bdNet.subtract(lineItem.getProgramBenefitValues().getProgramBenefits());
		}
		return bdNet;
	}
	
	private void applyBundleLineItemAuth(DealBundleDetail lineItemBundle, QuoteItem lineItem, Calendar authDate,
			BigDecimal bdNetPrice, String requestAction, String quoteNumber, String routeToMarket) {
		String authBasisDesc = getAuthBasisDesc(lineItem, bdNetPrice, routeToMarket);
		if (requestAction.equals("LOW_TOUCH_VERSION_ADD")) {
			if (StringUtils.isNotEmpty(authBasisDesc)) {
				authBasisDesc = authBasisDesc + " from " + quoteNumber;
			} else {
				authBasisDesc = quoteNumber;
			}
		}
		if (authBasisDesc != null) {
			if (authDate == null) {
				authDate = getCalendar(new Date(), "authDate");
//				lineItemBundle.setAuthorizsedDate((authDate.getTime()));
				String authDate1 = dateToXMLGregorianCalendar(authDate.getTime()).toString();
				if(authDate1 != null)
					lineItemBundle.setAuthorizedDate(authDate1.substring(0, authDate1.length()-1));
			}
			lineItemBundle.setAuthorizationStatus("Y");
			lineItemBundle.setAuthorizationStatusDescription(authBasisDesc);
//#N/A			lineItemBundle.setAuthBasisDesc(authBasisDesc);
		}
	}
	
	private void addGuidanceDetails(DealBundleDetail bundleLine, QuoteItem configLineItem, Calendar lastPricedDate){
		bundleLine.setGuidanceAvail("N");
		if (configLineItem.getAdditionalInfo().getTypicalGd() != null) {
			bundleLine.setGuidanceAvail("Y");
			if (configLineItem.getAdditionalInfo().getGdId() != null) {
				Long guidanceDetailsID = new Long(configLineItem.getAdditionalInfo().getGdId());
				bundleLine.setGuidanceDetailID(String.valueOf(guidanceDetailsID));
			}else{
				bundleLine.setGuidanceDetailID("0");
			}
			if (configLineItem.getAdditionalInfo().getExpertGd() != null) {
//#N/A				bundleLine.setGuidanceDiscExpertPct(configLineItem.getAdditionalInfo().getExpertGd().abs());
			}
			if (configLineItem.getAdditionalInfo().getFloorGd() != null) {
//#N/A				bundleLine.setGuidanceDiscFloorPct(configLineItem.getAdditionalInfo().getFloorGd().abs());
			}
			if (configLineItem.getAdditionalInfo().getTypicalGd() != null) {
				bundleLine.setTypicalAddPercentage(String.valueOf(configLineItem.getAdditionalInfo().getTypicalGd().abs()));
			}
			bundleLine.setGuidanceRefreshDate((lastPricedDate.getTime()).toString());
			bundleLine.setGuidanceRefreshDate(null);
		}
	}
	
	private void additionalBundleLineDetails(DealBundleDetail bundleLine, QuoteItem configLineItem, String dealType, boolean dealRegApplied){

		bundleLine.setListPriceAmount(String.valueOf(configLineItem.getLclUntLstAmt()));
		bundleLine.setOptCD(configLineItem.getProductOption());
		bundleLine.setDivisionCode(configLineItem.getProductLine());
//#N/A		bundleLine.setProdBaseNr(configLineItem.getProductNr());
		if(configLineItem.getProductDescription() != null){
			bundleLine.setProductDescription(StringUtils.substring(configLineItem.getProductDescription(), 0, 65));
		}
		if(configLineItem.getAdditionalInfo().getUiQuantity() != null)
		bundleLine.setSourceSystemQuantity(configLineItem.getAdditionalInfo().getUiQuantity().toString());
		dealRegAtLineItem(dealRegApplied, configLineItem);
//		if (configLineItem.getProgramBenefitValues() != null
//				&& configLineItem.getProgramBenefitValues().getProgramBenefits() != null) {
//#N/A			bundleLine.setCurrencyProgramBenefit(configLineItem.getProgramBenefitValues().getProgramBenefits());
//		}

		// bundleLine.setRqstDiscPct(configLineItem.get);
		// ys fix PA is negative in eclipse issue
		if (configLineItem.getPaDiscountPercent() != null) {
			if (dealType != null
					&& StringUtils.equalsIgnoreCase(Constants.DealType.TACTICAL_GIVEAWAY, dealType)) {
//#N/A				bundleLine.setStdDiscPct(null);
			} else {
//				bundleLine.setStdDiscPct(configLineItem.getPaDiscountPercent().abs());
			}

		}

	}
	
	@Override
	public Quote processDealCreationResponse(S4DealCreationResponse s4DealCreationResult, Quote quote,
			HashMap<String, Integer> lineItemMap, String requestAction, String priceTermCD,
			boolean alternatePriceTermCd, boolean isWin) {
		if(s4DealCreationResult == null)
			return quote;
		long startTime = new Date().getTime();
		String dealResponse = "";
		JSONObject err = s4DealCreationResult.getError();
		CreatedDealDetail createdDealDetails = s4DealCreationResult.getCreatedDealDetails();
		if(createdDealDetails == null){
			quote.setSfdcStatus("Processing Quote");
			quote.setSlsQtnVrsnSttsCd("Processing Quote");
			quote.addStatusMessages("Exception", "S4 Deal Creation Failure : <br>"+ err.getJSONObject("message").getString("value"));
			return quote;
		}
			
		if ("HIGH_TOUCH_VERSION".equalsIgnoreCase(requestAction)
				|| "HIGH_TOUCH".equalsIgnoreCase(requestAction)
				|| "HIGH_TOUCH_VERSION_ADD".equalsIgnoreCase(requestAction)) {
			quote.getAdditionalInfo().setHighTouch(true);
			if (quote.getAdditionalInfo().getFirstHighTouchCreated() == 0) {
				quote.getAdditionalInfo().setFirstHighTouchCreated(new Date().getTime());
			}
		}
		// Fix for CR 312320 : Implemented Deal creation success or failure logic based
		// on FSA or FSN(for low touch) and UNN (for high touch)
		dealResponse = createdDealDetails.getDealVersionStatusName() + createdDealDetails.getBidDeskManagementExpertApprovalCode()
				+ createdDealDetails.getDealStatusName() + " ";
		if ("Y".equalsIgnoreCase(createdDealDetails.getHighRiskIndicator())) {
			// If high risk flag is Y Do not do anything -- It will go For EUV
			if (isWin) {
				if (!quote.getAdditionalInfo().isOPGCreatedOnce()) {
					quote.getAdditionalInfo().setOPGCreatedOnce(true);
				}
			}
		}
		// For OPG turned on
		else if (isWin) {
			if (!quote.getAdditionalInfo().isOPGCreatedOnce()) {
				quote.getAdditionalInfo().setOPGCreatedOnce(true);
			}
			// If the sequence is FSA for other regions and FSN for APJ -- For Low touch
			/*if ("F".equalsIgnoreCase(createdDealDetails.getDealVersionStatusName())
					&& "S".equalsIgnoreCase(createdDealDetails.getBidDeskManagementExpertApprovalCode())
					&& ("A".equalsIgnoreCase(createdDealDetails.getDealStatusName())
							|| "".equalsIgnoreCase(createdDealDetails.getDealStatusName())
							|| "N".equalsIgnoreCase(createdDealDetails.getDealStatusName())
							|| "P".equalsIgnoreCase(createdDealDetails.getDealStatusName())
							|| createdDealDetails.getDealStatusName() == null)) {
				quote.getAdditionalInfo().setWonDealCreationFailed(false);
			} else {
				quote.getAdditionalInfo().setWonDealCreationFailed(true);
			}*/

		}
		// For OPG turned on
		else if ("F".equalsIgnoreCase(createdDealDetails.getDealVersionStatusName())
				&& "S".equalsIgnoreCase(createdDealDetails.getBidDeskManagementExpertApprovalCode())
				&& ("A".equalsIgnoreCase(createdDealDetails.getDealStatusName())
						|| "".equalsIgnoreCase(createdDealDetails.getDealStatusName())
						|| "N".equalsIgnoreCase(createdDealDetails.getDealStatusName())
						|| "P".equalsIgnoreCase(createdDealDetails.getDealStatusName())
						|| createdDealDetails.getDealStatusName() == null)) {
			// If the sequence is FSA for other regions and FSN for APJ -- For Low touch
			quote.getAdditionalInfo().setResellerFallout(false);
		}

		// For High touch quote
		else if ("U".equalsIgnoreCase(createdDealDetails.getDealVersionStatusName())
				&& "N".equalsIgnoreCase(createdDealDetails.getBidDeskManagementExpertApprovalCode())
				&& (createdDealDetails.getDealStatusName() == null
						|| "N".equalsIgnoreCase(createdDealDetails.getDealStatusName())
						|| "".equalsIgnoreCase(createdDealDetails.getDealStatusName()))) {
			// If the sequence is UNN -- For High touch
			quote.getAdditionalInfo().setResellerFallout(false);
		} else {
			quote.getAdditionalInfo().setResellerFallout(true);
		}
		String errType = null;
		if (alternatePriceTermCd) {
			quote.setIncoterm("DDP");
			quote.getAdditionalInfo().setOrigPriceListType(quote.getPriceListType());
			// above line hardcoded to DDP for now.. Need to be fetched from ezprs response
			// OR deal matrix based on price term cd?? Yili/Famil to discuss more...
			quote.setPriceListType(null);
		}
		// For rtm indirect -- Setting OPG start date and end date after successfull win
		// deal creation
		// Commenting out from here -- Based extension logic on effectivets and expiryts
		/*
		 * if (isWin) { quote.setOpgEndDate(quote.getExpiryTs());
		 * quote.setOpgStartDate(quote.getEffectiveTs()); } else {
		 * quote.setQuoteEndDate(quote.getExpiryTs());
		 * quote.setQuoteStartDate(quote.getEffectiveTs()); }
		 */
//		List<DealCreationResultType> dealCreationResults = multiDealCreationResult.getDealCreationResults().getDealCreationResult();
//		for (DealCreationResultType dealCreationResult : dealCreationResults) {
			String version = createdDealDetails.getDealVersionNumber();
			version=version.substring(version.length()-2);
			Long dealNo = Long.valueOf(createdDealDetails.getDealIdentifier());
			Long dealNoVersion = Long.valueOf(version);
			LOG.info("createDeal Response dealNo : " + dealNo);
			LOG.info("createDeal Response dealNoVersion  :" + dealNoVersion);

			if (requestAction.equals("HIGH_TOUCH") || requestAction.equals("HIGH_TOUCH_VERSION")
					|| requestAction.equals("HIGH_TOUCH_VERSION_ADD") || requestAction.indexOf("EIS_HIGH_TOUCH") >= 0) {
				// High touch deal created - clear LowTchDealNr
				quote.getAdditionalInfo().setLowTchDealNr((long) 0);
				// Set high touch deal number
				quote.getAdditionalInfo().setHiTchDealNr(dealNo);
			} else {
				// Low Touch deal created - clear out HiTchDealNr
				quote.getAdditionalInfo().setHiTchDealNr((long) 0);
				// Set the low touch deal number
				quote.getAdditionalInfo().setLowTchDealNr(dealNo);
				if (Constants.NgqConstants.INDIRECT.equalsIgnoreCase(quote.getGoToMarketRoute())) {
					OptimusUtil optimusUtil = new OptimusUtil();
					// CR 313197: Any new MCC code from the new deal should replace the existing
					// deal MCC for all line items
					String eclipseResultMCChargeCD = null;
					if (createdDealDetails != null
							&& StringUtils.isNotEmpty(createdDealDetails.getMiscellaneousChargeCode())) {
						eclipseResultMCChargeCD = createdDealDetails.getMiscellaneousChargeCode();
					}
					optimusUtil.calculatePricesForIndirectPricing(quote, requestAction, eclipseResultMCChargeCD);
					quote.setUpdateAdjustablePrices(PriceCalculatorEnum.UPDATETOTALSNOREQUESTEDBENEFITS.getCode());
				}

			}
			quote.setDealNr(String.valueOf(dealNo));
			quote.setDealVersion(version);
			/* setting mcchargecd */
			if (createdDealDetails != null
					&& createdDealDetails.getMiscellaneousChargeCode() != null) {
				quote.getAdditionalInfo().setDealMcChargeCd(createdDealDetails.getMiscellaneousChargeCode());
			}
			String statusMsg = "";
			if(createdDealDetails.getDealIdentifier() != null && createdDealDetails.getDealVersionNumber() != null)
			statusMsg = "Deal " + createdDealDetails.getDealIdentifier().toString() + ", Version "
					+ version + " successfully created.";

			if (createdDealDetails.getHighRiskIndicator() != null && createdDealDetails.getHighRiskIndicator() == "Y") {
			//Handle Deal Messages from S4
				List<DealMessages> warningMessages = createdDealDetails.getDealMessage();
				for (DealMessages warningMessage : warningMessages) {
					if (warningMessage.getWarningmessage1().equals("PKG: Unable to quote the deal")) {
						statusMsg = statusMsg + " Warning: " + warningMessage.getWarningmessage1() + ".";
					}
					if (warningMessage.getWarningmessage1().startsWith("PKG: High Risk Reasons")) {
						statusMsg = statusMsg + " Warning: " + warningMessage.getWarningmessage1() + ".";
					}
				}

				if (quote.getGoToMarketRoute() != null
						&& Constants.NgqConstants.INDIRECT.equalsIgnoreCase(quote.getGoToMarketRoute())) {
					// only if the deal is marked high risk and the EUV is not completed set this
					// flag
					// 07-nov-2018 Changed Logic
					// Regardless of deal being high risk - Only if EUV flag is Y a deal should go
					// for EUV submission
					if (quote.getFlags().get(QtFlagType.HIGHRISKDEALFLAG) == null)
						createFlagBasedOnFlagType(quote, QtFlagType.HIGHRISKDEALFLAG);
//EUV					LOG.info("EUV flag value from eclipse :" + dealCreationResult.getEUVRequiredFl().value());
//					if (dealCreationResult.getEUVRequiredFl() != null) {
//						quote.getFlags().get(QtFlagType.HIGHRISKDEALFLAG).setFlgVl("true");
//						if (!euvSubmitRequired) {
//							statusMsg = statusMsg + " Warning: High Risk Deal.";
//						} else {
//							statusMsg = statusMsg
//									+ " Warning: High Risk Deal. Quote cannot be completed unless EUV process is completed.";
//						}
//						if (Constants.NgqConstants.RESELLER.equalsIgnoreCase(quote.getOriginatingReferenceAsset())
//								|| Constants.NgqConstants.DISTRIBUTOR
//										.equalsIgnoreCase(quote.getOriginatingReferenceAsset())) {
//							// emailService.configureEmail(quote, Constants.EmailScenario.EUV_REQUIRED);
//							this.exeEmailInThread(quote, Constants.EmailScenario.EUV_REQUIRED);
//						}
//					} else {
//						if (quote.getFlags().get(QtFlagType.HIGHRISKDEALFLAG) != null) {
//							quote.getFlags().get(QtFlagType.HIGHRISKDEALFLAG).setFlgVl(null);
//						}
//					}
				}
				// quote.addStatusMessages("Warning", statusMsg);
				errType = "Warning";
			} else {
				// quote.addStatusMessages("Info", statusMsg);
				errType = "Info";
			}

			List<DealItemDetail> lineItems = createdDealDetails.getDealItemDetails();
			if (!(lineItems.size() == 0)) {
				HashMap<Integer, String> docNumMap = new HashMap<Integer, String>();
				for (DealItemDetail lineItem : lineItems) {
					LOG.info("createDeal Response getConfig_id() : " + lineItem.getConfigurationIdentifier());
					/*LOG.info("createDeal Response getSRC_CONFIG_ID()  :" + lineItem.getSRC_CONFIG_ID());*/
//Needed					LOG.info("createDeal Response getDocNum() : " + lineItem.getDocNum());
//NF					LOG.info("createDeal Response getLine_type_cd() : " + lineItem.getLINEPROGCD());
//					docNumMap.put(lineItem.getDocNum(), lineItem.getConfigId());
				}
				// for eCommerce we need config id and as eclipse used the same, so not setting
				// it back
				if (!quote.getCatalogMaintenanceQuote()) {
					// update bundleId
					for (QuoteItem qtitem : quote.getItems()) {
						if (("Config").equalsIgnoreCase(qtitem.getLineTypeCd())) {
							//Commented as per change requested in CR#334028 - Do not clear config header configid on Quote complete
							//qtitem.setBundleId(null);
						} else {
							if (lineItemMap.containsKey(qtitem.getSlsQtnItmSqnNr())) {
								Integer lineItemDocNum = lineItemMap.get(qtitem.getSlsQtnItmSqnNr());
								if (docNumMap.containsKey(lineItemDocNum)) {
									LOG.debug("createDeal Response Setting " + qtitem.getProductId() + ":"
											+ qtitem.getSlsQtnItmSqnNr() + ":" + qtitem.getBundleId()
											+ " to Eclipse bundleId " + docNumMap.get(lineItemDocNum));
									qtitem.setBundleId(docNumMap.get(lineItemDocNum));
								}
							}
						}
					}
				}

				quote.addStatusMessages(errType, statusMsg);
				LOG.info("createDeal addStatusMessages :" + quote.getStatusContainer().toString());
			} else {
				LOG.info("DealCreationResultType errorMessage: There are no lineItems in EDMS response");
				String msg1 = "Deal creation error. There are no lineItems in EDMS response";
				String lclAddStatusMessages = CommonUtil.getLoclizedMsg("NGEEDM_DL_ERR_NO_LN_RESP", null, msg1);
				quote.addStatusMessages("Exception", lclAddStatusMessages);
				// OPG Creation Failed
				if (isWin && isNotHighRiskDeal(quote)) {
					// emailService.configureEmail(quote,
					// Constants.EmailScenario.OPG_CREATION_FAILURE);
					this.exeEmailInThread(quote, Constants.EmailScenario.OPG_CREATION_FAILURE);
				} else {
					// emailService.configureEmail(quote,
					// Constants.EmailScenario.DEAL_CREATION_FAILURE);
					this.exeEmailInThread(quote, Constants.EmailScenario.DEAL_CREATION_FAILURE);
				}
			}
//NF			if (dealCreationResult.getOPGNumber() != null) {
//				quote.getAdditionalInfo().setOpgId(dealCreationResult.getOPGNumber());
//			}
//		}

		return quote;
	}
	
	private Quote createFlagBasedOnFlagType(Quote quote, QtFlagType qtFlgTyp) {
		Map<QtFlagType, QtFlag> flags = quote.getFlags();
		QtFlag flag = new QtFlag(qtFlgTyp, true);
		flags.put(qtFlgTyp, flag);
		quote.setFlags(flags);
		return quote;
	}
	
	@Override
	public void processDealCreateNewVersionResponse(S4DealCreationResponse s4DealCreateNewVersionResult, Quote quote,
			HashMap<String, Integer> lineItemMap, String requestAction, String priceTermCD,
			boolean alternatePriceTermCd, boolean isWin) {
		if(s4DealCreateNewVersionResult == null)
			return ;
		JSONObject err = s4DealCreateNewVersionResult.getError();
		CreatedDealDetail createdDealDetails = s4DealCreateNewVersionResult.getCreatedDealDetails();
		if(createdDealDetails == null){
			quote.setSfdcStatus("Processing Quote");
			quote.setSlsQtnVrsnSttsCd("Processing Quote");
			quote.addStatusMessages("Exception", "S4 Deal Creation Failure : <br>"+ err.getJSONObject("message").getString("value"));
			return;
		}
		String errType = null;
		// We have the below snippet of code for creating a deal with version:1
		// Implementing the same logic for new versions also.
		if (alternatePriceTermCd) {
			quote.setIncoterm("DDP");
			quote.getAdditionalInfo().setOrigPriceListType(quote.getPriceListType());
			// above line hardcoded to DDP for now.. Need to be fetched from ezprs response
			// OR deal matrix based on price term cd?? Yili/Famil to discuss more...
			quote.setPriceListType(null);
		}

		// For rtm indirect -- Setting OPG start date and end date after successfull win
		// deal creation
		if (isWin) {
			quote.setOpgEndDate(quote.getExpiryTs());// TODO check this line
			quote.setOpgStartDate(quote.getEffectiveTs());
		}
		String version = createdDealDetails.getDealVersionNumber();
		version=version.substring(version.length()-2);
		Long dealNo = Long.valueOf(createdDealDetails.getDealIdentifier());
		Long dealNoVersion = Long.valueOf(version);
		LOG.info("createDeal Response dealNo : " + dealNo);
		LOG.info("createDeal Response dealNoVersion  :" + dealNoVersion);
		if (requestAction.equals("HIGH_TOUCH") || requestAction.equals("HIGH_TOUCH_VERSION")
				|| requestAction.equals("HIGH_TOUCH_VERSION_ADD") || requestAction.indexOf("EIS_HIGH_TOUCH") >= 0) {
			quote.getAdditionalInfo().setHiTchDealNr(dealNo);
			// High touch deal created - clear LowTchDealNr
			quote.getAdditionalInfo().setLowTchDealNr((long) 0);
		} else {
			// Low Touch deal created - clear out HiTchDealNr
			quote.getAdditionalInfo().setHiTchDealNr((long) 0);
			// Set the low touch deal number
			quote.getAdditionalInfo().setLowTchDealNr(dealNo);
			if (Constants.NgqConstants.INDIRECT.equalsIgnoreCase(quote.getGoToMarketRoute())) {
				OptimusUtil optimusUtil = new OptimusUtil();
				// CR 313197: Any new MCC code from the new deal should replace the existing
				// deal MCC for all line items
				String eclipseResultMCChargeCD = null;
				if (createdDealDetails != null
						&& StringUtils.isNotEmpty(createdDealDetails.getMiscellaneousChargeCode())) {
					eclipseResultMCChargeCD = createdDealDetails.getMiscellaneousChargeCode();
				}
				optimusUtil.calculatePricesForIndirectPricing(quote, requestAction, eclipseResultMCChargeCD);
				quote.setUpdateAdjustablePrices(PriceCalculatorEnum.UPDATETOTALSNOREQUESTEDBENEFITS.getCode());
			}
		}
		quote.setDealNr(String.valueOf(dealNo));
		quote.setDealVersion(version);
		String statusMsg = "Deal " + createdDealDetails.getDealIdentifier().toString() + ", Version "
				+ version + " successfully created.";

		if (createdDealDetails.getHighRiskIndicator() != null
				&& createdDealDetails.getHighRiskIndicator() == "Y") {
			List<DealMessages> warningMessages = createdDealDetails.getDealMessage();
			for (DealMessages warningMessage : warningMessages) {
				if (warningMessage.getWarningmessage1().equals("PKG: Unable to quote the deal")) {
					statusMsg = statusMsg + " Warning: " + warningMessage.getWarningmessage1() + ".";
				}
				if (warningMessage.getWarningmessage1().startsWith("PKG: High Risk Reasons")) {
					statusMsg = statusMsg + " Warning: " + warningMessage.getWarningmessage1() + ".";
				}
			}
			if (quote.getGoToMarketRoute() != null
					&& Constants.NgqConstants.INDIRECT.equalsIgnoreCase(quote.getGoToMarketRoute())) {

				// only if the deal is marked high risk and the EUV is not completed set this
				// flag
				// 07-nov-2018 Changed Logic
				// Regardless of deal being high risk - Only if EUV flag is Y a deal should go
				// for EUV submission
//EUV				LOG.info("EUV flag value from eclipse :" + dealCreateNewVersionResult.getEUVRequiredFl().value());
//				if (quote.getFlags().get(QtFlagType.HIGHRISKDEALFLAG) == null)
//					createFlagBasedOnFlagType(quote, QtFlagType.HIGHRISKDEALFLAG);
//				if (dealCreateNewVersionResult.getEUVRequiredFl() != null) {
//					quote.getFlags().get(QtFlagType.HIGHRISKDEALFLAG).setFlgVl("true");
//					statusMsg = statusMsg
//							+ " Warning: High Risk Deal. Quote cannot be completed unless EUV process is completed.";
//
//					if (Constants.NgqConstants.RESELLER.equalsIgnoreCase(quote.getOriginatingReferenceAsset())
//							|| Constants.NgqConstants.DISTRIBUTOR
//									.equalsIgnoreCase(quote.getOriginatingReferenceAsset())) {
//						// emailService.configureEmail(quote, Constants.EmailScenario.EUV_REQUIRED);
//						this.exeEmailInThread(quote, Constants.EmailScenario.EUV_REQUIRED);
//					}
//
//				} else {
//					if (quote.getFlags().get(QtFlagType.HIGHRISKDEALFLAG) != null) {
//						quote.getFlags().get(QtFlagType.HIGHRISKDEALFLAG).setFlgVl(null);
//					}
//				}
			}
			// quote.addStatusMessages("Warning", statusMsg);
			errType = "Warning";
		} else {
			errType = "Info";
			// quote.addStatusMessages("Info", statusMsg);
		}

		List<DealItemDetail> lineItems = createdDealDetails.getDealItemDetails();
		if (!(lineItems.size() == 0)) {
			HashMap<Integer, String> docNumMap = new HashMap<Integer, String>();
			for (DealItemDetail lineItem : lineItems) {
				// this is the ucid
				LOG.info("createDeal Response getConfig_id() : " + lineItem.getConfigurationIdentifier());
				//LOG.info("createDeal Response getSRC_CONFIG_ID()  :" + lineItem.getSRC_CONFIG_ID());
				// this is the eclipse bundle id
//Needed				LOG.info("createDeal Response getDocNum() : " + lineItem.getDocNum());
//				LOG.info("createDeal Response getLine_type_cd() : " + lineItem.getLINEPROGCD());
//				docNumMap.put(lineItem.getDocNum(), lineItem.getConfigId());
			}

			for (QuoteItem qtitem : quote.getItems()) {
				// for eCommerce we need config id and as eclipse used the same, so not setting
				// it back
				if (quote.getCatalogMaintenanceQuote()) {
					continue;
				}
				if (("Config").equalsIgnoreCase(qtitem.getLineTypeCd())) {
					//Commented as per change requested in CR#334439(334028) - Do not clear config header configid on Quote complete
					//qtitem.setBundleId(null);
				} else {
					if (lineItemMap.containsKey(qtitem.getSlsQtnItmSqnNr())) {
						Integer lineItemDocNum = lineItemMap.get(qtitem.getSlsQtnItmSqnNr());
						if (docNumMap.containsKey(lineItemDocNum)) {
							LOG.debug("createDeal Response Setting " + qtitem.getProductId() + ":"
									+ qtitem.getSlsQtnItmSqnNr() + ":" + qtitem.getBundleId() + " to Eclipse bundleId "
									+ docNumMap.get(lineItemDocNum));
							qtitem.setBundleId(docNumMap.get(lineItemDocNum));
						}
					}
				}
			}
			quote.addStatusMessages(errType, statusMsg);
			LOG.info("createDeal addStatusMessages :" + quote.getStatusContainer().toString());
		} else {
			LOG.info("DealCreationResultType errorMessage: There are no lineItems in EDMS response");
			String msg1 = "Deal creation error. There are no lineItems in EDMS response";
			String lclAddStatusMessages = CommonUtil.getLoclizedMsg("NGEEDM_DL_ERR_NO_LN_RESP", null, msg1);
			quote.addStatusMessages("Exception", lclAddStatusMessages);
			// OPG Creation Failed
			if (isWin && isNotHighRiskDeal(quote)) {
				// emailService.configureEmail(quote,
				// Constants.EmailScenario.OPG_CREATION_FAILURE);
				this.exeEmailInThread(quote, Constants.EmailScenario.OPG_CREATION_FAILURE);
			} else {
				// emailService.configureEmail(quote,
				// Constants.EmailScenario.DEAL_CREATION_FAILURE);
				this.exeEmailInThread(quote, Constants.EmailScenario.DEAL_CREATION_FAILURE);
			}
		}
//		if (dealCreateNewVersionResult.getOPGNumber() != null) {
//			quote.getAdditionalInfo().setOpgId(dealCreateNewVersionResult.getOPGNumber());
//		}
	}
	
	@Override
	public S4DealCreationResponse s4DealService(S4DealCreationRequest s4DealCreationRequest) {
		S4DealCreationResponse s4DealResponse = new S4DealCreationResponse();
		if(s4DealCreationRequest == null)
			return null;
		String response = null;
		Gson gson = new Gson();
		JSONObject s4Request = new JSONObject(gson.toJson(s4DealCreationRequest));
		Gson gson1 = new Gson();
		gson1.toJson(s4DealResponse);
		LOG.debug("S4DEALCREATIONREQUEST : " + s4Request);
		LOG.info("S4DEALCREATIONREQUEST : " + s4Request);
		System.out.println("S4DEALCREATIONREQUEST : " + s4Request);
		String serviceURL = "https://deal-services-itg.its.hpecorp.net/api/deals";
		ClientResponse dealServiceResponse = null;
		try {
			String truststorefilename = System.getProperty("CONFIG_DIR");
			truststorefilename += "/" + System.getProperty("ENV") + "/truststore.jks";
			//System.setProperty("javax.net.ssl.trustStore", truststorefilename);
			//System.setProperty("javax.net.ssl.trustStorePassword", "truststore");
			ClientConfig config = new DefaultClientConfig();
			config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(getHostnameVerifier(), getSSLContext()));
			Client restClient = Client.create(config);
			WebResource webResource = restClient.resource(serviceURL);
//			JSONObject requestPayload = new JSONObject(s4Request);
			dealServiceResponse = webResource.type(MediaType.APPLICATION_JSON).post(ClientResponse.class,
					s4Request.toString());
			dealServiceResponse.bufferEntity();
			response = dealServiceResponse.getEntity(String.class);
			JSONObject err = new JSONObject(response);
			LOG.info("S4DEALCREATIONRESPONSE : " + response);
			System.err.println("S4 Deal Response : " + response);
			FileReqResUtil.getInstance().writeInOutAsAFile(s4DealCreationRequest.getDealSourceSystemIdentifier(),
					new JSONObject(s4DealCreationRequest).toString(), new JSONObject(response).toString(),
					ServiceEnum.S4DEAL, "DealCreate", FileReqResUtil.EXT_JSON, FileReqResUtil.EXT_JSON);
//			ObjectMapper mapper = new ObjectMapper();
//			mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
//			s4DealResponse = mapper.readValue(response, S4DealCreationResponse.class);
			if(err.has("error")){
				s4DealResponse.setError(err.getJSONObject("error"));
			}else{
				s4DealResponse = gson1.fromJson(response.toString(),S4DealCreationResponse.class);
			}
			return s4DealResponse;
//			return gson.fromJson(response, S4DealCreationResponse.class);
		} catch (Exception e) {
			e.printStackTrace();
			// TODO Auto-generated catch block
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			StringBuffer response1 = new StringBuffer();
			response1.append(" ");
			response1.append(sw.toString()); 
			response =response1.toString();
		}
		return s4DealResponse;
//		return gson.fromJson(response, S4DealCreationResponse.class);
	}
	
	private HostnameVerifier getHostnameVerifier() {
        return new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
                return true;
            }
        };
    }
	
	private SSLContext getSSLContext() {
		SSLContext sslContext = null;
		try {
			String truststorefilename = System.getProperty("CONFIG_DIR");
			truststorefilename += "/" + System.getProperty("ENV") + "/truststore.jks";
			KeyStore clientKeyStore = KeyStore.getInstance("JKS");
			InputStream readStream = new FileInputStream(truststorefilename);
			clientKeyStore.load(readStream, "truststore".toCharArray());
			KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyFactory.init(clientKeyStore, "wipro@123".toCharArray());
			KeyManager[] km = keyFactory.getKeyManagers();

			sslContext = SSLContext.getInstance("TLSv1.2");
			sslContext.init(km, null, null);
			SSLContext.setDefault(sslContext);
			return sslContext;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return sslContext;
    }
	

}
