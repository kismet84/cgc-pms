package com.cgcpms.contract.dto;

import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ContractSaveRequest {

    @Valid
    private CtContract contract;

    @Valid
    @Size(max = 200, message = "合同明细不能超过200条")
    private List<CtContractItem> items;

    @Valid
    @Size(max = 200, message = "付款条款不能超过200条")
    private List<CtContractPaymentTerm> paymentTerms;

    /**
     * @deprecated 当前未使用，考虑在 compositeSave 末尾调用 submitForApproval
     */
    @Deprecated
    private Boolean submitForApproval;
}
