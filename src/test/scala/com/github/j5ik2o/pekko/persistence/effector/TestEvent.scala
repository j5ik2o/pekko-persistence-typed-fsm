package com.github.j5ik2o.pekko.persistence.effector

// テスト用のイベント、状態、メッセージの定義をトップレベルで定義
// これによりテストクラスからの不要な参照を防ぎます
enum TestEvent {
  case TestEventA(value: String)
  case TestEventB(value: Int)
}
