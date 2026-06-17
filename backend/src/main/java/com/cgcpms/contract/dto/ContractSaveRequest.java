package com.cgcpms.contract.dto;

import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import lombok.Data;

import java.util.List;

@Data
public class ContractSaveRequest {

    private CtContract contract;

    private List<CtContractItem> items;

    private List<CtContractPaymentTerm> paymentTerms;

    private Boolean submitForApproval;
}
