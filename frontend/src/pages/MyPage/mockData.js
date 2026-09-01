// src/pages/MyPage/mockData.js
// ────────────────────────────────────────────────────────────────
// 마이페이지 전체에서 사용하는 단일 목업 데이터 소스.
// MyPage.jsx 와 모든 서브페이지(BidStatus, Likes, SuccessfulBid,
// OrderStatus, InquiryList, UploadSell …)
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
