-- 1) Grupos (sem dependências)
CREATE TABLE IF NOT EXISTS Grupos (
  GID          INT AUTO_INCREMENT PRIMARY KEY,
  nome_grupo   VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- 2) Usuários (já com coluna KID, mas sem FK para Chaveiro ainda)
CREATE TABLE IF NOT EXISTS Usuarios (
  UID               INT AUTO_INCREMENT PRIMARY KEY,
  login_email       VARCHAR(150) NOT NULL UNIQUE,
  nome              VARCHAR(100) NOT NULL,
  senha_bcrypt      VARCHAR(60)  NOT NULL,
  totp_secret_enc   VARBINARY(255) NOT NULL,
  GID               INT          NOT NULL,
  KID               INT          NULL,
  criado_em         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (GID) REFERENCES Grupos(GID)
    ON UPDATE CASCADE
    ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 3) Mensagens (independente)
CREATE TABLE IF NOT EXISTS Mensagens (
  MID    INT AUTO_INCREMENT PRIMARY KEY,
  codigo VARCHAR(10)  NOT NULL UNIQUE,
  texto  VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

-- 4) Chaveiro (FK para Usuarios. Aqui gera-se o KID que depois será referenciado em Usuarios)
CREATE TABLE IF NOT EXISTS Chaveiro (
  KID             INT AUTO_INCREMENT PRIMARY KEY,
  UID             INT NOT NULL,
  cert_pem        TEXT           NOT NULL,
  private_key_enc VARBINARY(4096) NOT NULL,
  criado_em       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (UID) REFERENCES Usuarios(UID)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5) Registros (FK para Mensagens e Usuarios, com timestamp automático)
CREATE TABLE IF NOT EXISTS Registros (
  RID       BIGINT AUTO_INCREMENT PRIMARY KEY,
  timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  MID       INT      NOT NULL,
  UID       INT      NULL,
  detalhes  TEXT     NULL,
  FOREIGN KEY (MID) REFERENCES Mensagens(MID)
    ON UPDATE CASCADE
    ON DELETE RESTRICT,
  FOREIGN KEY (UID) REFERENCES Usuarios(UID)
    ON UPDATE CASCADE
    ON DELETE SET NULL
) ENGINE=InnoDB;

-- 6) Agora que Chaveiro existe, adicionamos a FK circular em Usuarios.KID
ALTER TABLE Usuarios
  ADD CONSTRAINT fk_usuarios_chaveiro
    FOREIGN KEY (KID) REFERENCES Chaveiro(KID)
      ON UPDATE CASCADE
      ON DELETE RESTRICT;

-- 7) Índice para acelerar buscas de Registros por UID
CREATE INDEX idx_registros_uid ON Registros(UID);