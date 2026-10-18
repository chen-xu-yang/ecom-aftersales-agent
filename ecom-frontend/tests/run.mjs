// 前端测试入口：node tests/run.mjs
import { TestContext } from './helpers.mjs'
import rendererTests from './renderer.test.mjs'
import apiTests from './api.test.mjs'
import routerTests from './router.test.mjs'

const context = new TestContext()

rendererTests(context)
await apiTests(context)
routerTests(context)

const ok = context.summary()
process.exit(ok ? 0 : 1)
