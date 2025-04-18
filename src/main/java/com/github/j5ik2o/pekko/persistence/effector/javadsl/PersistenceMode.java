package com.github.j5ik2o.pekko.persistence.effector.javadsl;

public enum PersistenceMode {
  /** 通常の永続化モード（ディスクに保存） */
  PERSISTENCE,

  /** インメモリモード（ディスクに保存しない） */
  EPHEMERAL
}
