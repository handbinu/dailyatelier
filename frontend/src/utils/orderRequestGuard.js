export const createOrderRequestGuard = () => {
  const activeOrderIds = new Set()

  return {
    begin(orderId) {
      if (activeOrderIds.has(orderId)) return false
      activeOrderIds.add(orderId)
      return true
    },
    end(orderId) {
      activeOrderIds.delete(orderId)
    },
    isActive(orderId) {
      return activeOrderIds.has(orderId)
    },
  }
}
