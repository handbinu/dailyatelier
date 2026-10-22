export const createLatestRequest = () => {
  let activeRequest = null
  let generation = 0
  let disposed = false

  const isCurrent = (request) =>
    !disposed && activeRequest === request && request.generation === generation

  return {
    async run(execute, { onStart, onSuccess, onError, onFinally } = {}) {
      if (disposed) return

      activeRequest?.controller.abort()

      const request = {
        controller: new AbortController(),
        generation: ++generation,
      }
      activeRequest = request
      onStart?.()

      try {
        const result = await execute({ signal: request.controller.signal })
        if (isCurrent(request)) onSuccess?.(result)
        return result
      } catch (error) {
        if (isCurrent(request) && !request.controller.signal.aborted) {
          onError?.(error)
        }
      } finally {
        if (isCurrent(request)) {
          activeRequest = null
          onFinally?.()
        }
      }
    },

    dispose() {
      disposed = true
      generation += 1
      activeRequest?.controller.abort()
      activeRequest = null
    },
  }
}
