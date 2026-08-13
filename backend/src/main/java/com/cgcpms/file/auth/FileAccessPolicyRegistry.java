package com.cgcpms.file.auth;

import com.cgcpms.common.exception.BusinessException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable single source for supported file business types and their policy ownership. */
final class FileAccessPolicyRegistry {

    enum Group {
        PROJECT_COLLABORATION,
        COMMERCIAL_FINANCE,
        PROCUREMENT_MASTER_DATA,
        REVENUE_MEASUREMENT,
        QUALITY_SUPPLIER,
        TECHNICAL_CLOSEOUT
    }

    enum AuthorityMode {
        GENERIC,
        CASH_JOURNAL,
        FIXED,
        VARIATION,
        BID,
        MEASUREMENT
    }

    enum BusinessType {
        PROJECT(Group.PROJECT_COLLABORATION, AuthorityMode.GENERIC,
                Set.of(), Set.of(), "project:query", null),
        PROJECT_FILE(Group.PROJECT_COLLABORATION, AuthorityMode.FIXED,
                Set.of("project:file:query"), Set.of("project:file:manage"), null, null),
        PROJECT_COMMENCEMENT(Group.PROJECT_COLLABORATION, AuthorityMode.FIXED,
                Set.of("project:commencement:query"), Set.of("project:commencement:edit"),
                "project:commencement:query", null),
        COMMUNICATION_MESSAGE(Group.PROJECT_COLLABORATION, AuthorityMode.FIXED,
                Set.of("communication:view"), Set.of("communication:send"), null, null),

        CONTRACT(Group.COMMERCIAL_FINANCE, AuthorityMode.GENERIC,
                Set.of(), Set.of(), "contract:query", null),
        INVOICE(Group.COMMERCIAL_FINANCE, AuthorityMode.FIXED,
                Set.of("invoice:query"), Set.of("invoice:edit"), "invoice:query", null),
        RECEIPT(Group.PROCUREMENT_MASTER_DATA, AuthorityMode.GENERIC,
                Set.of(), Set.of(), "receipt:query", null),
        MATERIAL_RECEIPT(Group.PROCUREMENT_MASTER_DATA, AuthorityMode.FIXED,
                Set.of("receipt:query"), Set.of("receipt:edit"), "receipt:query", "receipt:query"),
        PAYMENT(Group.COMMERCIAL_FINANCE, AuthorityMode.FIXED,
                Set.of("payment:app:query"), Set.of("payment:app:edit"),
                "payment:app:query", "payment:app:query"),
        EXPENSE(Group.COMMERCIAL_FINANCE, AuthorityMode.FIXED,
                Set.of("expense:query"), Set.of("expense:edit"), "expense:query", null),
        SUBCONTRACT(Group.COMMERCIAL_FINANCE, AuthorityMode.FIXED,
                Set.of("subcontract:measure:query"), Set.of("subcontract:measure:edit"),
                "subcontract:measure:query", null),
        SETTLEMENT(Group.COMMERCIAL_FINANCE, AuthorityMode.FIXED,
                Set.of("settlement:query"), Set.of("settlement:edit"),
                "settlement:query", "settlement:query"),
        VARIATION(Group.COMMERCIAL_FINANCE, AuthorityMode.VARIATION,
                Set.of("variation:order:query", "variation:trace"), Set.of(), null, null),
        BID_COST(Group.PROCUREMENT_MASTER_DATA, AuthorityMode.BID,
                Set.of("bid:query"), Set.of("bid:file:manage"), "bid:query", null),
        PARTNER(Group.PROCUREMENT_MASTER_DATA, AuthorityMode.GENERIC,
                Set.of(), Set.of(), null, null),
        MATERIAL(Group.PROCUREMENT_MASTER_DATA, AuthorityMode.GENERIC,
                Set.of(), Set.of(), null, null),
        CASH_JOURNAL(Group.COMMERCIAL_FINANCE, AuthorityMode.CASH_JOURNAL,
                Set.of(), Set.of(), "cashbook:journal:query", null),
        SITE_DAILY_LOG(Group.PROJECT_COLLABORATION, AuthorityMode.FIXED,
                Set.of("site:daily:query"), Set.of("site:daily:edit"), "site:daily:query", null),

        PURCHASE_REQUEST(Group.PROCUREMENT_MASTER_DATA, AuthorityMode.FIXED,
                Set.of("purchase:request:list"), Set.of("purchase:request:edit"),
                "purchase:request:list", "purchase:request:list"),
        PURCHASE_ORDER(Group.PROCUREMENT_MASTER_DATA, AuthorityMode.FIXED,
                Set.of("purchase:order:query"), Set.of("purchase:order:edit"),
                "purchase:order:query", "purchase:order:query"),
        CONTRACT_REVENUE(Group.REVENUE_MEASUREMENT, AuthorityMode.FIXED,
                Set.of("revenue:operations:query"), Set.of("revenue:operations:maintain"),
                "revenue:operations:query", null),
        OWNER_SETTLEMENT(Group.REVENUE_MEASUREMENT, AuthorityMode.FIXED,
                Set.of("revenue:operations:query"), Set.of("revenue:operations:maintain"),
                "revenue:operations:query", null),
        SALES_INVOICE(Group.REVENUE_MEASUREMENT, AuthorityMode.FIXED,
                Set.of("revenue:operations:query"), Set.of("revenue:operations:maintain"),
                "revenue:operations:query", null),
        COLLECTION_RECORD(Group.REVENUE_MEASUREMENT, AuthorityMode.FIXED,
                Set.of("revenue:operations:query"), Set.of("revenue:operations:maintain"),
                "revenue:operations:query", null),
        PRODUCTION_MEASUREMENT(Group.REVENUE_MEASUREMENT, AuthorityMode.MEASUREMENT,
                Set.of("measurement:query"), Set.of(), "measurement:query", null),
        OWNER_MEASUREMENT_SUBMISSION(Group.REVENUE_MEASUREMENT, AuthorityMode.FIXED,
                Set.of("measurement:query"), Set.of("measurement:owner:review"),
                "measurement:query", null),

        QS_INSPECTION(Group.QUALITY_SUPPLIER, AuthorityMode.FIXED,
                Set.of("quality:safety:query"), Set.of("quality:safety:inspection:maintain"), null, null),
        QS_ISSUE(Group.QUALITY_SUPPLIER, AuthorityMode.FIXED,
                Set.of("quality:safety:query"), Set.of("quality:safety:inspection:maintain"), null, null),
        QS_RECTIFICATION(Group.QUALITY_SUPPLIER, AuthorityMode.FIXED,
                Set.of("quality:safety:query"),
                Set.of("quality:safety:rectify", "quality:safety:reinspect"), null, null),
        SUPPLIER_SOURCING(Group.QUALITY_SUPPLIER, AuthorityMode.FIXED,
                Set.of("supplier:sourcing:query"), Set.of("supplier:sourcing:maintain"), null, null),
        SUPPLIER_QUOTE(Group.QUALITY_SUPPLIER, AuthorityMode.FIXED,
                Set.of("supplier:sourcing:query"), Set.of("supplier:sourcing:quote"), null, null),

        TECH_SCHEME(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("technical:query"), Set.of("technical:scheme:maintain", "technical:scheme:submit"),
                null, null),
        TECH_DRAWING_VERSION(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("technical:query"), Set.of("technical:drawing:receive"), null, null),
        TECH_DRAWING_REVIEW(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("technical:query"), Set.of("technical:drawing:review"), null, null),
        TECH_RFI(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("technical:query"), Set.of("technical:rfi:raise"), null, null),
        TECH_RFI_RESPONSE(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("technical:query"), Set.of("technical:rfi:respond", "technical:rfi:accept"), null, null),
        TECH_DISCLOSURE(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("technical:query"), Set.of("technical:disclosure:maintain"), null, null),
        TECH_ARCHIVE(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("technical:query"), Set.of("technical:archive:confirm"), null, null),
        CLOSEOUT_SECTION_ACCEPTANCE(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("closeout:query"), Set.of("closeout:section:maintain"), null, null),
        CLOSEOUT_FINAL_ACCEPTANCE(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("closeout:query"), Set.of("closeout:acceptance:submit"), null, null),
        CLOSEOUT_DEFECT(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("closeout:query"), Set.of("closeout:defect:maintain", "closeout:defect:verify"),
                null, null),
        CLOSEOUT_WARRANTY(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("closeout:query"), Set.of("closeout:warranty:maintain"), null, null),
        CLOSEOUT_ARCHIVE_TRANSFER(Group.TECHNICAL_CLOSEOUT, AuthorityMode.FIXED,
                Set.of("closeout:query"), Set.of("closeout:archive:maintain"), null, null);

        private final Group group;
        private final AuthorityMode authorityMode;
        private final Set<String> readAuthorities;
        private final Set<String> writeAuthorities;
        private final String sourceReadAuthority;
        private final String generatedDocumentAuthority;

        BusinessType(Group group,
                     AuthorityMode authorityMode,
                     Set<String> readAuthorities,
                     Set<String> writeAuthorities,
                     String sourceReadAuthority,
                     String generatedDocumentAuthority) {
            this.group = group;
            this.authorityMode = authorityMode;
            this.readAuthorities = Set.copyOf(readAuthorities);
            this.writeAuthorities = Set.copyOf(writeAuthorities);
            this.sourceReadAuthority = sourceReadAuthority;
            this.generatedDocumentAuthority = generatedDocumentAuthority;
        }

        Group group() {
            return group;
        }

        AuthorityMode authorityMode() {
            return authorityMode;
        }

        Set<String> authorities(boolean write) {
            return write ? writeAuthorities : readAuthorities;
        }

        String sourceReadAuthority() {
            return sourceReadAuthority;
        }

        String generatedDocumentAuthority() {
            return generatedDocumentAuthority;
        }
    }

    private static final Map<String, BusinessType> BUSINESS_TYPES = buildBusinessTypes();

    private final Map<BusinessType, FileAccessPolicy> policiesByType;

    FileAccessPolicyRegistry(List<FileAccessPolicy> policies) {
        EnumMap<Group, FileAccessPolicy> policiesByGroup = new EnumMap<>(Group.class);
        for (FileAccessPolicy policy : List.copyOf(policies)) {
            if (policy == null || policy.group() == null) {
                throw new IllegalStateException("Unknown file access policy group");
            }
            FileAccessPolicy duplicate = policiesByGroup.putIfAbsent(policy.group(), policy);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate file access policy group: " + policy.group());
            }
        }

        EnumSet<Group> missingGroups = EnumSet.allOf(Group.class);
        missingGroups.removeAll(policiesByGroup.keySet());
        if (!missingGroups.isEmpty()) {
            throw new IllegalStateException("Missing file access policy groups: " + missingGroups);
        }

        EnumMap<BusinessType, FileAccessPolicy> resolvedPolicies = new EnumMap<>(BusinessType.class);
        for (BusinessType businessType : BusinessType.values()) {
            FileAccessPolicy policy = policiesByGroup.get(businessType.group());
            if (policy == null) {
                throw new IllegalStateException("Missing file access policy: " + businessType);
            }
            if (resolvedPolicies.putIfAbsent(businessType, policy) != null) {
                throw new IllegalStateException("Duplicate file access policy: " + businessType);
            }
        }
        this.policiesByType = Map.copyOf(resolvedPolicies);
    }

    BusinessType find(String businessType) {
        if (businessType == null) return null;
        return BUSINESS_TYPES.get(businessType.toUpperCase(Locale.ROOT));
    }

    BusinessType require(String businessType) {
        BusinessType resolved = find(businessType);
        if (resolved == null) {
            throw new BusinessException("FILE_BIZ_TYPE_UNKNOWN", "不支持的业务类型: " + businessType);
        }
        return resolved;
    }

    FileAccessPolicy policyFor(BusinessType businessType) {
        FileAccessPolicy policy = policiesByType.get(businessType);
        if (policy == null) {
            throw new IllegalStateException("Missing file access policy: " + businessType);
        }
        return policy;
    }

    static Set<String> knownTypeNames() {
        return BUSINESS_TYPES.keySet();
    }

    private static Map<String, BusinessType> buildBusinessTypes() {
        Map<String, BusinessType> businessTypes = new HashMap<>();
        for (BusinessType businessType : BusinessType.values()) {
            if (businessTypes.putIfAbsent(businessType.name(), businessType) != null) {
                throw new IllegalStateException("Duplicate file business type: " + businessType.name());
            }
        }
        return Map.copyOf(businessTypes);
    }
}
