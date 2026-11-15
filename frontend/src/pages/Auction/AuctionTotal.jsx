import ArtListingPage from '../Search/ArtListingPage'

const PRESETS = {
  total: { title: '전체 경매', subtitle: '지금 경매가 진행 중인 전체 작품을 만나보세요.', resultTitle: '진행 중인 전체 작품', preset: { status: 'ONGOING' } },
  digital: { title: '디지털 경매', subtitle: '지금 경매가 진행 중인 디지털 작품을 만나보세요.', resultTitle: '진행 중인 디지털 작품', preset: { status: 'ONGOING', format: 'DIGITAL' } },
  analog: { title: '실물 경매', subtitle: '지금 경매가 진행 중인 실물 작품을 만나보세요.', resultTitle: '진행 중인 실물 작품', preset: { status: 'ONGOING', format: 'PHYSICAL' } },
}

export default function AuctionTotal({ type = 'total' }) {
  const config = PRESETS[type] || PRESETS.total
  return <ArtListingPage auction eyebrow="DAILY AUCTION" {...config} />
}
