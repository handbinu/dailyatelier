export const FORMATS = ['DIGITAL', 'PHYSICAL']
export const CATEGORIES = ['OIL_PAINTING', 'WATERCOLOR', 'ACRYLIC_PAINTING', 'DRAWING', 'DIGITAL_ART', 'PRINTMAKING', 'PHOTOGRAPHY', 'SCULPTURE', 'CRAFT', 'MIXED_MEDIA', 'OTHER']
export const STATUSES = ['UPCOMING', 'ONGOING', 'ENDED']
export const SORTS = ['ENDING_SOON', 'NEWEST', 'PRICE_ASC', 'PRICE_DESC']

const allowed = { format: FORMATS, category: CATEGORIES, status: STATUSES, sort: SORTS }

export function normalizeSearchParams(input, {
  allowedKeys = ['q', 'artist', 'format', 'category', 'status', 'sort', 'page'],
  allowedCategories = CATEGORIES,
} = {}) {
  const normalized = new URLSearchParams()
  for (const key of ['q', 'artist']) {
    if (!allowedKeys.includes(key)) continue
    const value = input.get(key)?.trim()
    if (value) normalized.set(key, value)
  }
  for (const key of ['format', 'category', 'status']) {
    if (!allowedKeys.includes(key)) continue
    const value = input.get(key)
    const values = key === 'category' ? allowedCategories : allowed[key]
    if (values.includes(value)) normalized.set(key, value)
  }
  const sort = input.get('sort')
  if (allowedKeys.includes('sort') && SORTS.includes(sort) && sort !== 'ENDING_SOON') normalized.set('sort', sort)
  const page = Number(input.get('page'))
  if (allowedKeys.includes('page') && Number.isSafeInteger(page) && page > 1) normalized.set('page', String(page))
  return normalized
}

export function readSearchState(params) {
  return {
    q: params.get('q') || '', artist: params.get('artist') || '',
    format: params.get('format') || '', category: params.get('category') || '', status: params.get('status') || '',
    sort: params.get('sort') || 'ENDING_SOON', page: Number(params.get('page') || 1),
  }
}
