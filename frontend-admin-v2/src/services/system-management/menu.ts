import { apiRequest } from '@/services/request'
import { requiredId } from './support'

export interface MenuRecord {
  id: string
  parentId: string
  menuName: string
  menuType: 'DIR' | 'MENU' | 'BUTTON'
  path?: string
  component?: string
  perms?: string
  icon?: string
  orderNum: number
  status: string
  visible: number
}

export interface MenuCommand {
  parentId: string
  menuName: string
  menuType: MenuRecord['menuType']
  path: string
  component: string
  perms: string
  icon: string
  orderNum: number
  status: string
  visible: number
}

export function loadMenus(): Promise<MenuRecord[]> {
  return apiRequest<MenuRecord[]>('/system/menus').then((rows) => rows.map(normalizeMenu))
}

export function loadMenu(id: string): Promise<MenuRecord> {
  return apiRequest<MenuRecord>(`/system/menus/${requiredId(id)}`).then(normalizeMenu)
}

export function createMenu(command: MenuCommand): Promise<string> {
  return apiRequest<string, MenuCommand>('/system/menus', {
    method: 'POST',
    body: command,
  }).then(String)
}

export function updateMenu(id: string, command: MenuCommand): Promise<void> {
  return apiRequest<void, MenuCommand>(`/system/menus/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function deleteMenu(id: string): Promise<void> {
  return apiRequest<void>(`/system/menus/${requiredId(id)}`, { method: 'DELETE' })
}

function normalizeMenu(row: MenuRecord): MenuRecord {
  return {
    ...row,
    id: String(row.id),
    parentId: String(row.parentId ?? 0),
    orderNum: Number(row.orderNum ?? 0),
    visible: Number(row.visible ?? 1),
  }
}
