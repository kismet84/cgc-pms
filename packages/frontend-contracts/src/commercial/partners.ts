export interface PartnerQuery {
  pageNo?: number;
  pageSize?: number;
  partnerCode?: string;
  partnerName?: string;
  partnerType?: string;
  status?: string;
}

export interface PartnerRecord {
  id: string;
  partnerCode: string;
  partnerName: string;
  partnerType?: string | null;
  status?: string | null;
}
