export type IssueStatus = 'PENDING' | 'IN_PROGRESS' | 'RESOLVED'

export interface Issue {
  id: number
  title: string
  description: string
  status: IssueStatus
  priority: string
  latitude: number
  longitude: number
  imageUrl: string | null
  reportCount?: number
}
