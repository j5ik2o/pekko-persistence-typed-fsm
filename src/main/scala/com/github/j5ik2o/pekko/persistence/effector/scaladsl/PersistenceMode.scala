package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * 永続化モードを表す列挙型
 */
enum PersistenceMode {

  /** 通常の永続化モード（ディスクに保存） */
  case Persisted

  /** インメモリモード（ディスクに保存しない） */
  case Ephemeral
}
