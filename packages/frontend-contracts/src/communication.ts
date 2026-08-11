export type ConversationType = "DIRECT" | "GROUP";
export type ConversationStatus = "ACTIVE" | "CLOSED";
export type CommunicationRole = "OWNER" | "ADMIN" | "MEMBER";

export interface CommunicationUserSummary {
  id: string;
  username: string;
  realName?: string | null;
  avatar?: string | null;
}

export interface CommunicationMemberSummary {
  userId: string;
  username?: string | null;
  realName?: string | null;
  avatar?: string | null;
  role: CommunicationRole;
  userStatus?: string | null;
}

export interface AttachmentRecord {
  id: string;
  originalName: string;
  fileSize: number;
  contentType: string;
  virusScanStatus: string;
}

export interface ConversationSummary {
  id: string;
  type: ConversationType;
  name: string;
  ownerUserId?: string | null;
  lastMessageSeq: string;
  lastMessageAt?: string | null;
  status: ConversationStatus;
  role: CommunicationRole;
  unreadCount: number;
}

export interface MessageRecord {
  id: string;
  conversationId: string;
  senderId: string;
  seq: string | null;
  body?: string | null;
  senderName: string;
  createdAt: string;
  attachments: AttachmentRecord[];
}

export interface CommunicationUnreadCount {
  count: number;
}

export interface CommunicationEvent {
  action: "REFRESH" | "PING" | string;
  conversationId?: string | null;
  messageId?: string | null;
  seq?: string | null;
}
