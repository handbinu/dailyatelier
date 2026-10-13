import path from 'node:path'
import { test as base } from './qaPage'

const storageState = (role: 'buyer' | 'artist' | 'admin') =>
  path.resolve('.playwright', `${role}.json`)

export const buyerTest = base.extend({
  storageState: storageState('buyer'),
})

export const artistTest = base.extend({
  storageState: storageState('artist'),
})

export const adminTest = base.extend({
  storageState: storageState('admin'),
})
