// 轻量测试工具：不引入额外依赖，直接用 Node 运行
export class TestContext {
  constructor() {
    this.passed = 0
    this.failed = 0
    this.failures = []
  }

  test(name, fn) {
    try {
      fn()
      this.passed += 1
      console.log(`PASS ${name}`)
    } catch (error) {
      this.failed += 1
      this.failures.push({ name, error })
      console.log(`FAIL ${name} :: ${error.message}`)
    }
  }

  summary() {
    console.log(`\nSUMMARY passed=${this.passed} failed=${this.failed}`)
    for (const failure of this.failures) {
      console.log(`\n--- ${failure.name} ---`)
      console.log(failure.error.stack)
    }
    return this.failed === 0
  }
}

export function assert(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

export function assertEqual(actual, expected, message = '') {
  if (actual !== expected) {
    throw new Error(`${message}\n  期望: ${JSON.stringify(expected)}\n  实际: ${JSON.stringify(actual)}`)
  }
}

export function assertContains(haystack, needle, message = '') {
  if (!String(haystack).includes(needle)) {
    throw new Error(`${message}\n  期望包含: ${JSON.stringify(needle)}\n  实际: ${JSON.stringify(haystack)}`)
  }
}

export function assertNotContains(haystack, needle, message = '') {
  if (String(haystack).includes(needle)) {
    throw new Error(`${message}\n  不应包含: ${JSON.stringify(needle)}\n  实际: ${JSON.stringify(haystack)}`)
  }
}
