export type ProposalChannel = 'TA' | 'INTERN' | 'RA' | 'TEAM'

export interface ProposalGuide {
  introduction: string
  experience: string
  approach: string
  schedule: string
}

export type ProposalGuides = Record<ProposalChannel, ProposalGuide>

export function proposalChannel(role: string, postingMode?: string): ProposalChannel {
  if (postingMode === 'CHALLENGE' || role === 'RA') return 'RA'
  if (role === 'TA' || role === 'TEAM') return role
  return 'INTERN'
}


export function proposalGuideText(guide: ProposalGuide): string {
  return [guide.introduction, guide.experience, guide.approach, guide.schedule].filter(Boolean).join('\n\n')
}
