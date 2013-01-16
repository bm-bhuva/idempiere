package org.adempiere.process;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.FillMandatoryException;
import org.adempiere.util.ShippingUtil;
import org.compiere.model.MClientInfo;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MShipper;
import org.compiere.model.MShipperLabels;
import org.compiere.model.MShipperPackaging;
import org.compiere.model.MShipperPickupTypes;
import org.compiere.model.MShippingTransaction;
import org.compiere.model.MShippingTransactionLine;
import org.compiere.model.MSysConfig;
import org.compiere.model.MUOM;
import org.compiere.process.ProcessInfoLog;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.Trx;

public class SalesOrderRateInquiryProcess extends SvrProcess 
{
	private boolean p_IsPriviledgedRate = false;
	
	@Override
	protected void prepare() 
	{
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if (name.equals(MShippingTransaction.COLUMNNAME_IsPriviledgedRate))
				p_IsPriviledgedRate = ((String)para[i].getParameter()).equals("Y");
			else
				log.log(Level.SEVERE, "prepare - Unknown Parameter: " + name);
		}
    }
	
	@Override
	protected String doIt() throws Exception 
	{
		MOrder m_order = new MOrder(getCtx(), getRecord_ID(), get_TrxName());
		
		if (m_order.getM_Shipper_ID() == 0)
			throw new FillMandatoryException(MOrder.COLUMNNAME_M_Shipper_ID);
		
		MClientInfo ci = MClientInfo.get(getCtx(), getAD_Client_ID(), get_TrxName());
		if (ci.getC_ChargeFreight_ID() == 0 && ci.getM_ProductFreight_ID() == 0)
			throw new AdempiereException("Product or Charge for Freight is not defined at Client window > Client Info tab");
		if (ci.getC_UOM_Weight_ID() == 0)
			throw new AdempiereException("UOM for Weight is not defined at Client window > Client Info tab");
		if (ci.getC_UOM_Length_ID() == 0)
			throw new AdempiereException("UOM for Length is not defined at Client window > Client Info tab");
		
		MOrderLine[] ols = m_order.getLines(false, MOrderLine.COLUMNNAME_Line);
		if (ols.length == 0)
			throw new AdempiereException("@NoLines@");
		
		for (MOrderLine ol : ols)
		{
			if ((ol.getM_Product_ID() > 0 && ol.getM_Product_ID() == ci.getM_ProductFreight_ID()) ||
					(ol.getC_Charge_ID() > 0 && ol.getC_Charge_ID() == ci.getC_ChargeFreight_ID()))
				continue;
			else if (ol.getM_Product_ID() > 0)
			{
				MProduct product = new MProduct(getCtx(), ol.getM_Product_ID(), get_TrxName());
				
				BigDecimal weight = product.getWeight();
				if (weight == null || weight.compareTo(BigDecimal.ZERO) == 0)
					throw new AdempiereException("No weight defined for product " + product.toString());
			}
		}
		
		Trx trx = Trx.get(Trx.createTrxName("spt-"), true);
		boolean ok = false;
		MShippingTransaction st = null;
		try
		{			
			st = createShippingTransaction(m_order, MShippingTransaction.ACTION_RateInquiry, get_TrxName());
			ok = st.processOnline();
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, "processOnline", e);
		}
		
		if (trx != null)
		{
			trx.commit();
			trx.close();
		}
		
		if (ok)
		{
			MOrderLine freightLine = null;
			for (MOrderLine ol : ols)
			{
				if ((ol.getM_Product_ID() > 0 && ol.getM_Product_ID() == ci.getM_ProductFreight_ID()) ||
						(ol.getC_Charge_ID() > 0 && ol.getC_Charge_ID() == ci.getC_ChargeFreight_ID()))
				{
					freightLine = ol;
					break;
				}
			}
			
			if (freightLine == null)
			{
				freightLine = new MOrderLine(m_order);
			
				if (ci.getC_ChargeFreight_ID() > 0)
					freightLine.setC_Charge_ID(ci.getC_ChargeFreight_ID());
				else if (ci.getM_ProductFreight_ID() > 0)
					freightLine.setM_Product_ID(ci.getM_ProductFreight_ID());
				else
					throw new AdempiereException("Product or Charge for Freight is not defined at Client window > Client Info tab");
			}
			
			freightLine.setC_BPartner_Location_ID(m_order.getC_BPartner_Location_ID());
			freightLine.setM_Shipper_ID(m_order.getM_Shipper_ID());
			freightLine.setQty(BigDecimal.ONE);
			freightLine.setPrice(st.getPrice());
			freightLine.saveEx();
		}
		else
		{
			getProcessInfo().setError(true);
			getProcessInfo().addLog(new ProcessInfoLog(getProcessInfo().getAD_Process_ID(), 
					new Timestamp(System.currentTimeMillis()), null, st.getErrorMessage()));
			return st.getErrorMessage();
		}
		
		getProcessInfo().addLog(new ProcessInfoLog(getProcessInfo().getAD_Process_ID(), 
				new Timestamp(System.currentTimeMillis()), null, st.getShippingRespMessage()));
		return st.getShippingRespMessage();
	}
	
	private MShippingTransaction createShippingTransaction(MOrder m_order, String action, String trxName)
	{
		MShipper shipper = new MShipper(getCtx(), m_order.getM_Shipper_ID(), get_TrxName());
		String whereClause = "M_Shipper_ID = " + shipper.getM_Shipper_ID() + " AND IsDefault='Y' AND IsActive='Y'";
		int M_ShipperLabels_ID = 0;
		int[] ids = MShipperLabels.getAllIDs(MShipperLabels.Table_Name, whereClause, get_TrxName());
		if (ids.length > 0)
			M_ShipperLabels_ID = ids[0];

		int M_ShipperPackaging_ID = 0;
		ids = MShipperPackaging.getAllIDs(MShipperPackaging.Table_Name, whereClause, get_TrxName());
		if (ids.length > 0)
			M_ShipperPackaging_ID = ids[0];

		int M_ShipperPickupTypes_ID = 0;
		ids = MShipperPickupTypes.getAllIDs(MShipperPickupTypes.Table_Name, whereClause, get_TrxName());
		if (ids.length > 0)
			M_ShipperPickupTypes_ID = ids[0];
		
		String ShipperAccount = ShippingUtil.getSenderShipperAccount(shipper.getM_Shipper_ID(), shipper.getAD_Org_ID());
		String DutiesShipperAccount = ShippingUtil.getSenderDutiesShipperAccount(shipper.getM_Shipper_ID(), shipper.getAD_Org_ID());
		
		// 1 kg = 2.20462 lb
		MClientInfo ci = MClientInfo.get(getCtx(), getAD_Client_ID(), get_TrxName());
		MUOM uom = new MUOM(getCtx(), ci.getC_UOM_Weight_ID(), null);
		String unit = uom.getX12DE355();
		boolean isPound = false;
		if (unit != null)
		{
			unit = unit.toUpperCase();
			if (unit.equals("LB") || unit.equals("LBS"))
				isPound = true;
		}
		
		MShipperPackaging sp = new MShipperPackaging(getCtx(), M_ShipperPackaging_ID, get_TrxName());
		BigDecimal WeightPerPackage = sp.getWeight().multiply(isPound ? new BigDecimal(2.20462) : BigDecimal.ONE);
		
		if (WeightPerPackage == null || WeightPerPackage.compareTo(BigDecimal.ZERO) == 0)
		{
			BigDecimal defaultWeightPerPackage = new BigDecimal(MSysConfig.getDoubleValue(MSysConfig.SHIPPING_DEFAULT_WEIGHT_PER_PACKAGE, 30));
			WeightPerPackage = defaultWeightPerPackage.multiply(isPound ? new BigDecimal(2.20462) : BigDecimal.ONE);
		}
		
		BigDecimal CODAmount = m_order.getGrandTotal();
		BigDecimal CustomsValue = BigDecimal.ZERO;
		BigDecimal FreightAmt = BigDecimal.ZERO;
		BigDecimal TotalWeight = BigDecimal.ZERO;
		
		ArrayList<ShippingPackage> packages = new ArrayList<ShippingPackage>();
		BigDecimal TotalPackageWeight = BigDecimal.ZERO;
		ArrayList<MProduct> products = new ArrayList<MProduct>();
		MOrderLine[] ols = m_order.getLines(false, MOrderLine.COLUMNNAME_Line);
		for (MOrderLine ol : ols)
		{
			if ((ol.getM_Product_ID() > 0 && ol.getM_Product_ID() == ci.getM_ProductFreight_ID()) ||
					(ol.getC_Charge_ID() > 0 && ol.getC_Charge_ID() == ci.getC_ChargeFreight_ID()))
			{
//				FreightAmt = FreightAmt.add(ol.getLineNetAmt());
				continue;
			}
			else if (ol.getM_Product_ID() > 0)
			{
				MProduct product = new MProduct(getCtx(), ol.getM_Product_ID(), get_TrxName());
				
				BigDecimal weight = product.getWeight();
				if (weight == null || weight.compareTo(BigDecimal.ZERO) == 0)
					throw new AdempiereException("No weight defined for product " + product.toString());
				
				BigDecimal qty = ol.getQtyEntered();
				if (qty == null)
					qty = BigDecimal.ZERO;
				
				if (product.isOwnBox())
				{
					ArrayList<MProduct> ownBoxProducts = new ArrayList<MProduct>();
					ownBoxProducts.add(product);
					
					BigDecimal remainingQty = qty;
					for(int i = 0; i < qty.doubleValue(); i++)
					{
						remainingQty = remainingQty.subtract(BigDecimal.ONE);
						
						ShippingPackage shippingPackage = new ShippingPackage();
						shippingPackage.setWeight(weight.multiply(remainingQty));
						shippingPackage.setProducts(ownBoxProducts);
						shippingPackage.setHeight(product.getShelfHeight());
						shippingPackage.setWidth(new BigDecimal(product.getShelfWidth()));
						shippingPackage.setLength(new BigDecimal(product.getShelfDepth()));
						packages.add(shippingPackage);
					}
				}
				else
				{
					products.add(product);
					
					if ((weight.multiply(qty)).add(TotalPackageWeight).compareTo(WeightPerPackage) > 0)
					{
						ShippingPackage shippingPackage = new ShippingPackage();
						shippingPackage.setWeight(TotalPackageWeight);
						shippingPackage.setProducts(products);
						packages.add(shippingPackage);
						products.clear();
						TotalPackageWeight = weight.multiply(qty);
					}
					else
						TotalPackageWeight = TotalPackageWeight.add(weight.multiply(qty));
				}
					
				TotalWeight = TotalWeight.add(weight.multiply(qty));
			}
		}
		
		if (TotalPackageWeight.compareTo(BigDecimal.ZERO) > 0)
		{
			ShippingPackage shippingPackage = new ShippingPackage();
			shippingPackage.setWeight(TotalPackageWeight);
			shippingPackage.setProducts(products);
			packages.add(shippingPackage);
		}
		
		CustomsValue = CODAmount.subtract(FreightAmt);
		
		int BoxCount = packages.size();
		
		MShippingTransaction st = new MShippingTransaction(getCtx(), 0, trxName);
		st.setAction(action);
//		st.setAD_Client_ID(m_order.getAD_Client_ID());
		st.setAD_Org_ID(m_order.getAD_Org_ID());
		st.setAD_User_ID(m_order.getAD_User_ID());
		st.setBill_Location_ID(m_order.getBill_Location_ID());
		st.setBoxCount(BoxCount);
//		st.setC_BP_ShippingAcct_ID(getC_BP_ShippingAcct_ID());
		st.setC_BPartner_ID(m_order.getC_BPartner_ID());
		st.setC_BPartner_Location_ID(m_order.getC_BPartner_Location_ID());
		st.setC_Currency_ID(m_order.getC_Currency_ID());
//		st.setC_Invoice_ID(0);
		st.setC_Order_ID(m_order.getC_Order_ID());
		st.setC_UOM_Length_ID(ci.getC_UOM_Length_ID());
		st.setC_UOM_Weight_ID(ci.getC_UOM_Weight_ID());
//		st.setCashOnDelivery(isCashOnDelivery());
		st.setCODAmount(CODAmount);
		st.setCustomsValue(CustomsValue);
//		st.setDateReceived(getDateReceived());
//		st.setDeliveryConfirmation(isDeliveryConfirmation());
//		st.setDeliveryConfirmationType(getDeliveryConfirmationType());
//		st.setDescription(getDescription());
//		st.setDotHazardClassOrDivision(getDotHazardClassOrDivision());
//		st.setDryIceWeight(getDryIceWeight());
		st.setDutiesShipperAccount(DutiesShipperAccount);
//		st.setFOB(getFOB());
		st.setFreightAmt(FreightAmt);
		st.setFreightCharges(MShippingTransaction.FREIGHTCHARGES_PrepaidAndBill);
//		st.setHandlingCharge(getHandlingCharge());
//		st.setHeight(getHeight());
//		st.setHoldAddress(getHoldAddress());
//		st.setHomeDeliveryPremiumDate(getHomeDeliveryPremiumDate());
//		st.setHomeDeliveryPremiumPhone(getHomeDeliveryPremiumPhone());
//		st.setHomeDeliveryPremiumType(getHomeDeliveryPremiumType());
//		st.setInsurance(getInsurance());
//		st.setInsuredAmount(getInsuredAmount());
//		st.setIsAccessible(isAccessible());
		st.setIsActive(m_order.isActive());
//		st.setIsAddedHandling(isAddedHandling());
//		st.setIsAlternateReturnAddress(isAlternateReturnAddress());
//		st.setIsCargoAircraftOnly(isCargoAircraftOnly());
//		st.setIsDryIce(isDryIce());
//		st.setIsDutiable(isDutiable());
//		st.setIsFutureDayShipment(isFutureDayShipment());
//		st.setIsHazMat(isHazMat());
//		st.setIsHoldAtLocation(isHoldAtLocation());
//		st.setIsIgnoreZipNotFound(isIgnoreZipNotFound());
//		st.setIsIgnoreZipStateNotMatch(isIgnoreZipStateNotMatch());
		st.setIsPriviledgedRate(p_IsPriviledgedRate);
		st.setIsResidential(shipper.isResidential());
		st.setIsSaturdayDelivery(shipper.isSaturdayDelivery());
//		st.setIsSaturdayPickup(isSaturdayPickup());
//		st.setIsVerbalConfirmation(isVerbalConfirmation());
//		st.setLatestPickupTime(getLatestPickupTime());
//		st.setLength(getLength());
//		st.setM_InOut_ID(0);
//		st.setM_Package_ID(getM_Package_ID());
		st.setM_Shipper_ID(m_order.getM_Shipper_ID());
		st.setM_ShipperLabels_ID(M_ShipperLabels_ID);
		st.setM_ShipperPackaging_ID(M_ShipperPackaging_ID);
		st.setM_ShipperPickupTypes_ID(M_ShipperPickupTypes_ID);
		st.setM_ShippingProcessor_ID(shipper.getM_ShippingProcessor_ID());
		st.setM_Warehouse_ID(m_order.getM_Warehouse_ID());
//		st.setNotificationMessage(getNotificationMessage());
//		st.setNotificationType(getNotificationType());
		st.setPaymentRule(m_order.getPaymentRule());
		st.setPOReference(m_order.getPOReference());
//		st.setPrice(getPrice());
//		st.setPriceActual(getPriceActual());
//		st.setProcessed(isProcessed());
//		st.setReceivedInfo(getReceivedInfo());
//		st.setReturnBPartner_ID(getReturnBPartner_ID());
//		st.setReturnLocation_ID(getReturnLocation_ID());
//		st.setReturnUser_ID(getReturnUser_ID());
		st.setSalesRep_ID(m_order.getSalesRep_ID());
		st.setShipDate(m_order.getDatePromised());
		st.setShipperAccount(ShipperAccount);
//		st.setShippingRespMessage(ShippingRespMessage);
//		st.setSurcharges(getSurcharges());
		st.setTrackingInfo(shipper.getTrackingURL());
//		st.setTrackingNo(getTrackingNo());
		st.setWeight(TotalWeight);
//		st.setWidth(getWidth());
		st.saveEx();
		
		for (int i = 0; i < packages.size(); i++)
		{
			ShippingPackage shippingPackage = packages.get(i);
			
			MShippingTransactionLine stl = new MShippingTransactionLine(st.getCtx(), 0, st.get_TrxName());
//			stl.setAD_Client_ID(m_order.getAD_Client_ID());
			stl.setAD_Org_ID(m_order.getAD_Org_ID());
			stl.setC_UOM_Length_ID(ci.getC_UOM_Length_ID());
			stl.setC_UOM_Weight_ID(ci.getC_UOM_Weight_ID());
//			stl.setDescription(getDescription());
			stl.setHeight(shippingPackage.getHeight());
			stl.setIsActive(m_order.isActive());
			stl.setLength(shippingPackage.getLength());
//			stl.setM_PackageMPS_ID(0);
			stl.setM_ShippingTransaction_ID(st.getM_ShippingTransaction_ID());
//			stl.setMasterTrackingNo(getMasterTrackingNo());
//			stl.setPrice(getPrice());
//			stl.setProcessed(isProcessed());
			stl.setSeqNo((i+1) * 10);
//			stl.setTrackingNo(getTrackingNo());
			stl.setWeight(shippingPackage.getWeight());
			stl.setWidth(shippingPackage.getWidth());
			stl.saveEx();
		}
		
		return st;
	}
	
	class ShippingPackage
	{
		private BigDecimal weight;
		private BigDecimal height;
		private BigDecimal length;
		private BigDecimal width;
		private ArrayList<MProduct> products;
		
		public BigDecimal getWeight() {
			return weight;
		}
		
		public void setWeight(BigDecimal weight) {
			this.weight = weight;
		}
		
		public BigDecimal getHeight() {
			return height;
		}
		
		public void setHeight(BigDecimal height) {
			this.height = height;
		}
		
		public BigDecimal getLength() {
			return length;
		}
		
		public void setLength(BigDecimal length) {
			this.length = length;
		}
		
		public BigDecimal getWidth() {
			return width;
		}
		
		public void setWidth(BigDecimal width) {
			this.width = width;
		}
		
		public ArrayList<MProduct> getProducts() {
			return products;
		}
		
		public void setProducts(ArrayList<MProduct> products) {
			this.products = products;
		}
	}
}