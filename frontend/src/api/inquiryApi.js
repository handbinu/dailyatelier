import api from './authApi'

export const createInquiry = ({ inquiryType, title, content, emailAlert, attachment }) => {
  const formData = new FormData()
  formData.append('request', new Blob([JSON.stringify({ inquiryType, title, content, emailAlert })], {
    type: 'application/json',
  }))
  if (attachment) formData.append('attachment', attachment)
  return api.post('/api/inquiries', formData)
}

export const getMyInquiries = ({ page = 0, size = 50, signal } = {}) =>
  api.get('/api/inquiries/me', { params: { page, size }, signal })

export const getInquiryDetail = (inquiryId, { signal } = {}) =>
  api.get(`/api/inquiries/${inquiryId}`, { signal })

export const getAdminInquiries = ({ status = 'ALL', page = 0, size = 50, signal } = {}) =>
  api.get('/api/admin/inquiries', { params: { status, page, size }, signal })

export const answerInquiry = (inquiryId, answer) =>
  api.post(`/api/admin/inquiries/${inquiryId}/answer`, { answer })
