-- 1) Grupos (sem dependências)
CREATE TABLE IF NOT EXISTS Grupos (
  GID          INT AUTO_INCREMENT PRIMARY KEY,
  nome_grupo   VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- ► Seed inicial de Grupos
INSERT IGNORE INTO Grupos (nome_grupo) VALUES
  ('Administrador'),
  ('Usuário Comum');

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

-- ► Seed inicial de todas as mensagens usadas no Cofre Digital
INSERT IGNORE INTO Mensagens (MID, codigo, texto) VALUES
  (1001,'1001','Sistema iniciado.'),
  (1002,'1002','Sistema encerrado.'),
  (1003,'1003','Sessão iniciada para <login_name>.'),
  (1004,'1004','Sessão encerrada para <login_name>.'),
  (1005,'1005','Partida do sistema iniciada para cadastro do administrador.'),
  (1006,'1006','Partida do sistema iniciada para operação normal pelos usuários.'),
  (2001,'2001','Autenticação etapa 1 iniciada.'),
  (2002,'2002','Autenticação etapa 1 encerrada.'),
  (2003,'2003','Login name <login_name> identificado com acesso liberado.'),
  (2004,'2004','Login name <login_name> identificado com acesso bloqueado.'),
  (2005,'2005','Login name <login_name> não identificado.'),
  (3001,'3001','Autenticação etapa 2 iniciada para <login_name>.'),
  (3002,'3002','Autenticação etapa 2 encerrada para <login_name>.'),
  (3003,'3003','Senha pessoal verificada positivamente para <login_name>.'),
  (3004,'3004','Primeiro erro da senha pessoal contabilizado para <login_name>.'),
  (3005,'3005','Segundo erro da senha pessoal contabilizado para <login_name>.'),
  (3006,'3006','Terceiro erro da senha pessoal contabilizado para <login_name>.'),
  (3007,'3007','Acesso do usuario <login_name> bloqueado pela autenticação etapa 2.'),
  (4001,'4001','Autenticação etapa 3 iniciada para <login_name>.'),
  (4002,'4002','Autenticação etapa 3 encerrada para <login_name>.'),
  (4003,'4003','Token verificado positivamente para <login_name>.'),
  (4004,'4004','Primeiro erro de token contabilizado para <login_name>.'),
  (4005,'4005','Segundo erro de token contabilizado para <login_name>.'),
  (4006,'4006','Terceiro erro de token contabilizado para <login_name>.'),
  (4007,'4007','Acesso do usuario <login_name> bloqueado pela autenticação etapa 3.'),
  (5001,'5001','Tela principal apresentada para <login_name>.'),
  (5002,'5002','Opção 1 do menu principal selecionada por <login_name>.'),
  (5003,'5003','Opção 2 do menu principal selecionada por <login_name>.'),
  (5004,'5004','Opção 3 do menu principal selecionada por <login_name>.'),
  (6001,'6001','Tela de cadastro apresentada para <login_name>.'),
  (6002,'6002','Botão cadastrar pressionado por <login_name>.'),
  (6003,'6003','Senha pessoal inválida fornecida por <login_name>.'),
  (6004,'6004','Caminho do certificado digital inválido fornecido por <login_name>.'),
  (6005,'6005','Chave privada verificada negativamente para <login_name> (caminho inválido).'),
  (6006,'6006','Chave privada verificada negativamente para <login_name> (frase secreta inválida).'),
  (6007,'6007','Chave privada verificada negativamente para <login_name> (assinatura digital inválida).'),
  (6008,'6008','Confirmação de dados aceita por <login_name>.'),
  (6009,'6009','Confirmação de dados rejeitada por <login_name>.'),
  (6010,'6010','Botão voltar de cadastro para o menu principal pressionado por <login_name>.'),
  (7001,'7001','Tela de consulta de arquivos secretos apresentada para <login_name>.'),
  (7002,'7002','Botão voltar de consulta para o menu principal pressionado por <login_name>.'),
  (7003,'7003','Botão Listar de consulta pressionado por <login_name>.'),
  (7004,'7004','Caminho de pasta inválido fornecido por <login_name>.'),
  (7005,'7005','Arquivo de índice decriptado com sucesso para <login_name>.'),
  (7006,'7006','Arquivo de índice verificado (integridade e autenticidade) com sucesso para <login_name>.'),
  (7007,'7007','Falha na decriptação do arquivo de índice para <login_name>.'),
  (7008,'7008','Falha na verificação (integridade e autenticidade) do arquivo de índice para <login_name>.'),
  (7009,'7009','Lista de arquivos presentes no índice apresentada para <login_name>.'),
  (7010,'7010','Arquivo <arq_name> selecionado por <login_name> para decriptação.'),
  (7011,'7011','Acesso permitido ao arquivo <arq_name> para <login_name>.'),
  (7012,'7012','Acesso negado ao arquivo <arq_name> para <login_name>.'),
  (7013,'7013','Arquivo <arq_name> decriptado com sucesso para <login_name>.'),
  (7014,'7014','Arquivo <arq_name> verificado (integridade e autenticidade) com sucesso para <login_name>.'),
  (7015,'7015','Falha na decriptação do arquivo <arq_name> para <login_name>.'),
  (7016,'7016','Falha na verificação (integridade e autenticidade) do arquivo <arq_name> para <login_name>.'),
  (8001,'8001','Tela de saída apresentada para <login_name>.'),
  (8002,'8002','Botão encerrar sessão pressionado por <login_name>.'),
  (8003,'8003','Botão encerrar sistema pressionado por <login_name>.'),
  (8004,'8004','Botão voltar de sair para o menu principal pressionado por <login_name>.');

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