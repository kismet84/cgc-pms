package com.cgcpms.contract.dto;

import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class ContractSaveRequest {

    @Valid
    private CtContract contract;

    @Valid
    private List<CtContractItem> items;

    @Valid
    private List<CtContractPaymentTerm> paymentTerms;

    /**
     * @deprecated 当前未使用，考虑在 compositeSave 末尾调用 submitForApproval
     */
    @Deprecated
    private Boolean submitForApproval;
}
