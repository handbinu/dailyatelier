export const ART_IMAGE_PLACEHOLDER = '/img/art-placeholder.svg'

export const getArtImageSrc = (imgPath) =>
  typeof imgPath === 'string' && imgPath.trim() ? imgPath.trim() : ART_IMAGE_PLACEHOLDER

export const applyArtImageFallback = (event) => {
  const image = event.currentTarget
  if (image.dataset.fallbackApplied === 'true') return

  image.dataset.fallbackApplied = 'true'
  image.src = ART_IMAGE_PLACEHOLDER
}

export const applyArtImageFallbackIfBlank = (event) => {
  const image = event.currentTarget
  if (image.naturalWidth <= 1 || image.naturalHeight <= 1) {
    applyArtImageFallback(event)
  }
}
