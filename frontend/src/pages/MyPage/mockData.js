// src/pages/MyPage/mockData.js
// ────────────────────────────────────────────────────────────────
// 마이페이지 전체에서 사용하는 단일 목업 데이터 소스.
// MyPage.jsx 와 모든 서브페이지(BidStatus, Likes, SuccessfulBid,
// MyReview, OrderStatus, InquiryList, ArtistReview, UploadSell …)
// 가 여기서 import 해서 사용한다.
// ────────────────────────────────────────────────────────────────

export const MOCK_USER = {
    userId:        'daily_user',
    nickname:      '굴러다니는 안방귀신',
    name:          '김민주',
    email:         'art@dailyatelier.com',
    phoneNumber:   '010-1234-5678',
    reserve:       420000,
    usedReserve:   326000,
    joinDate:      '2024-03-12',
    address:       '서울특별시 중랑구 용마산로90길 28',
    addressDetail: '101호',
    zipCode:       '02535',
    profileImg:    null,
    userStatus:    1,
  }
  
  export const MOCK_BIDS = [
    { id: 'b1', artName: '엎질러진 자연', artImg: '/img/auction/new_1.jpg', artist: '박석계',   artType: '실물',   myPrice: 209001, currentPrice: 240000, closingTime: '2025-04-10 18:49', status: 'ongoing'  },
    { id: 'b2', artName: '노을',           artImg: '/img/auction/new_2.jpg', artist: '따스혀',   artType: '실물',   myPrice: 360064, currentPrice: 360064, closingTime: '2025-04-08 20:00', status: 'imminent' },
    { id: 'b3', artName: '목도리냥',       artImg: '/img/auction/new_3.jpg', artist: '어린아이', artType: '디지털', myPrice: 278200, currentPrice: 310000, closingTime: '2025-03-30 12:00', status: 'ended'    },
    { id: 'b4', artName: '도시의 단면',    artImg: '/img/auction/new_4.png', artist: '박석계',   artType: '디지털', myPrice: 120000, currentPrice: 185000, closingTime: '2025-04-12 15:00', status: 'ongoing'  },
  ]
  
  export const MOCK_LIKES = [
    { id: 'l1', artName: '기억의 조각', artImg: '/img/auction/done_digi_2.jpg', currentPrice: 430000, artist: '박석계',   status: 'ongoing', type: '디지털' },
    { id: 'l2', artName: '우주비행사',  artImg: '/img/auction/done_digi_3.jpg', currentPrice: 185000, artist: '따스혀',   status: 'ongoing', type: '디지털' },
    { id: 'l3', artName: '파도소리',    artImg: '/img/auction/done_digi_4.jpg', currentPrice: 520000, artist: '어린아이', status: 'ended',   type: '실물'   },
    { id: 'l4', artName: '봄날의 기억', artImg: '/img/slide_pic_1.jpg',         currentPrice: 380000, artist: '박석계',   status: 'ongoing', type: '실물'   },
  ]
  
  export const MOCK_SUCCESSFUL = [
    { id: 's1', artName: '연예인 병', artImg: '/img/auction/done_digi_1.jpg', finalPrice: 530000, artist: '박석계', reviewWritten: true,  orderedAt: '2025-03-15' },
    { id: 's2', artName: '숲속에서',  artImg: '/img/auction/done_real_1.jpg', finalPrice: 720000, artist: '따스혀', reviewWritten: false, orderedAt: '2025-03-28' },
  ]
  
  export const MOCK_REVIEWS = [
    { id: 'r1', artId: 's1', artName: '연예인 병', artImg: '/img/auction/done_digi_1.jpg', finalPrice: 530000, content: '너무 이뻐요! 제가 원하던 느낌 그대로예요. 방에 걸어놓으니 분위기가 확 달라졌어요. 작가님 다음 작품도 꼭 도전해볼게요.', star: 9.0, createdAt: '2025-03-20' },
  ]
  
  export const MOCK_ORDERS = [
    { id: 'o1', orderNo: '2025-0328-001', artName: '숲속에서',  artImg: '/img/auction/done_real_1.jpg', price: 720000, artist: '따스혀',   status: '입금완료', orderedAt: '2025-03-28' },
    { id: 'o2', orderNo: '2025-0315-002', artName: '연예인 병', artImg: '/img/auction/done_digi_1.jpg', price: 530000, artist: '박석계',   status: '배송완료', orderedAt: '2025-03-15' },
    { id: 'o3', orderNo: '2025-0210-003', artName: '노을',       artImg: '/img/auction/new_2.jpg',       price: 360000, artist: '어린아이', status: '취소',     orderedAt: '2025-02-10' },
  ]
  
  export const MOCK_INQUIRIES = [
    { id: 'q1', type: '배송',   title: '배송 현황은 어디서 확인하나요?',          answer: '마이페이지 > 주문 조회에서 확인하실 수 있습니다.',                                                   createdAt: '2025-03-10', answered: true  },
    { id: 'q2', type: '포인트', title: '포인트 환불 가능한가요?',                 answer: null,                                                                                                    createdAt: '2025-03-25', answered: false },
    { id: 'q3', type: '작품',   title: '작품 사진이 실물과 많이 다른 것 같아요.', answer: '작품 색상은 모니터 환경에 따라 차이가 날 수 있습니다. 자세한 문의는 1:1 채팅을 이용해주세요.',      createdAt: '2025-02-20', answered: true  },
  ]
  
  export const MOCK_MY_ARTS = [
    { id: 'a1', name: '어두운 내면', img: '/img/auction/new_5.png',       type: '디지털', startPrice: 80000,  currentPrice: 140000, status: 'ongoing',  bidCount: 5,  closingTime: '2025-04-15 18:00', material: '디지털 드로잉' },
    { id: 'a2', name: '바다의 기억', img: '/img/auction/new_2.jpg',       type: '실물',   startPrice: 200000, currentPrice: 200000, status: 'upcoming', bidCount: 0,  closingTime: '2025-04-20 12:00', material: '수채화' },
    { id: 'a3', name: '봄날의 기억', img: '/img/slide_pic_1.jpg',         type: '실물',   startPrice: 150000, currentPrice: 380000, status: 'ended',    bidCount: 12, closingTime: '2025-03-20 18:00', material: '유채' },
    { id: 'a4', name: '도시의 감성', img: '/img/auction/done_digi_2.jpg', type: '디지털', startPrice: 100000, currentPrice: 210000, status: 'ended',    bidCount: 8,  closingTime: '2025-02-28 18:00', material: '디지털 드로잉' },
  ]
  
  export const MOCK_ARTIST_REVIEWS = [
    { id: 'ar1', artName: '봄날의 기억', artImg: '/img/slide_pic_1.jpg',         buyer: '굴러다니는 안방귀신', content: '정말 감동적인 작품이에요. 벽에 걸어두니 집이 화사해졌어요!',                  star: 9.5,  createdAt: '2025-03-22', finalPrice: 380000 },
    { id: 'ar2', artName: '봄날의 기억', artImg: '/img/slide_pic_1.jpg',         buyer: '아트러버123',          content: '색감이 정말 살아있어요. 작가님 다음 작품도 기대됩니다.',                      star: 10.0, createdAt: '2025-03-24', finalPrice: 380000 },
    { id: 'ar3', artName: '도시의 감성', artImg: '/img/auction/done_digi_2.jpg', buyer: 'daily_fan',            content: '디지털임에도 질감이 느껴져요. 프린트해서 액자에 걸었더니 너무 좋아요.',        star: 8.5,  createdAt: '2025-03-01', finalPrice: 210000 },
    { id: 'ar4', artName: '도시의 감성', artImg: '/img/auction/done_digi_2.jpg', buyer: 'art_collector_99',     content: '구도가 독특하고 색감이 세련됐어요. 사무실에 두었더니 다들 어디서 샀냐고 묻네요.', star: 7.5,  createdAt: '2025-02-28', finalPrice: 210000 },
  ]
  
  // ── 공통 유틸 ─────────────────────────────────────────────────────
  export const fmt = (n) => Number(n).toLocaleString()
  
  export const STATUS_META = {
    ongoing:  { label: '진행 중',   color: 'green'  },
    imminent: { label: '종료 임박', color: 'orange' },
    ended:    { label: '종료',      color: 'gray'   },
    upcoming: { label: '경매 예정', color: 'blue'   },
  }
  
  export const ORDER_STATUS_COLOR = {
    '입금완료': 'blue',
    '배송중':   'orange',
    '배송완료': 'green',
    '취소':     'gray',
  }
  
  export const ART_STATUS_LABEL = {
    ongoing:  '경매 중',
    upcoming: '경매 예정',
    ended:    '경매 종료',
  }
  
  export const ART_STATUS_COLOR = {
    ongoing:  'green',
    upcoming: 'blue',
    ended:    'gray',
  }