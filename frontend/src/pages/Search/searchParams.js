export const FORMATS = ['DIGITAL', 'PHYSICAL']
export const CATEGORIES = ['OIL_PAINTING', 'WATERCOLOR', 'ACRYLIC_PAINTING', 'DRAWING', 'DIGITAL_ART', 'PRINTMAKING', 'PHOTOGRAPHY', 'SCULPTURE', 'CRAFT', 'MIXED_MEDIA', 'OTHER']
export const STATUSES = ['UPCOMING', 'ONGOING', 'ENDED']
export const SORTS = ['ENDING_SOON', 'NEWEST', 'PRICE_ASC', 'PRICE_DESC']

const allowed = { format: FORMATS, category: CATEGORIES, status: STATUSES, sort: SORTS }

export function normalizeSearchParams(input) {
  const normalized = new URLSearchParams()
  for (const key of ['q', 'artist']) {
    const value = input.get(key)?.trim()
    if (value) normalized.set(key, value)
  }
  for (const key of ['format', 'category', 'status']) {
    const value = input.get(key)
    if (allowed[key].includes(value)) normalized.set(key, value)
  }
  const sort = input.get('sort')
  if (SORTS.includes(sort) && sort !== 'ENDING_SOON') normalized.set('sort', sort)
  const page = Number(input.get('page'))
  if (Number.isSafeInteger(page) && page > 1) normalized.set('page', String(page))
  return normalized
}

export function readSearchState(params) {
  return {
    q: params.get('q') || '', artist: params.get('artist') || '',
    format: params.get('format') || '', category: params.get('category') || '', status: params.get('status') || '',
    sort: params.get('sort') || 'ENDING_SOON', page: Number(params.get('page') || 1),
  }
}
