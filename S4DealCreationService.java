package com.hpe.s4.integration.service.interfaces;

import java.util.HashMap;

import com.hp.om.beans.EISFormData;
import com.hp.om.beans.Quote;
import com.hpe.s4.deal.beans.S4DealCreationRequest;
import com.hpe.s4.deal.beans.S4DealCreationResponse;

public interface S4DealCreationService {
	
	public S4DealCreationRequest getS4DealCreationRequest(Quote quote, HashMap<String, Integer> lineItemMap,
			String requestAction, Boolean isWin, String priceTermCD, String addtDis, boolean alternatePriceTermCd,
			EISFormData eisObj);

	boolean fetchTreeDataForApplyDealReg(Quote quote, String requestAction, boolean isWin);

	boolean applyDrCategorySettings(Quote quote, String dealRequestAction);
	
	public Quote processDealCreationResponse(S4DealCreationResponse multiDealCreationResult, Quote quote,
			HashMap<String, Integer> lineItemMap, String requestAction, String priceTermCD,
			boolean alternatePriceTermCd, boolean isWin);
	
//	public Quote processDealCreationResponse(String multiDealCreationResult, Quote quote,
//			HashMap<String, Integer> lineItemMap, String requestAction, String priceTermCD,
//			boolean alternatePriceTermCd, boolean isWin);
	
	public void processDealCreateNewVersionResponse(S4DealCreationResponse dealCreateNewVersionResult, Quote quote,
			HashMap<String, Integer> lineItemMap, String requestAction, String priceTermCD,
			boolean alternatePriceTermCd, boolean isWin);
	
//	public void processDealCreateNewVersionResponse(String dealCreateNewVersionResult, Quote quote,
//			HashMap<String, Integer> lineItemMap, String requestAction, String priceTermCD,
//			boolean alternatePriceTermCd, boolean isWin);
	
	public S4DealCreationResponse s4DealService(S4DealCreationRequest s4DealCreationRequest);
	
//	public String s4DealService(S4DealCreationRequest s4DealCreationRequest);
	
	
}
