package service;

/**
 * Enum para códigos de evento e mensagens associadas.
 */
public enum EventCode {
    ADMIN_CREATED  ("1005", "Admin cadastrado com sucesso"),
    CERT_INVALID   ("6002", "Falha ao validar certificado"),
    PASSWORD_HASHED("6005", "Hash de senha gerado"),
    TOTP_GENERATED ("6006", "Segredo TOTP gerado e armazenado");

    private final String code;
    private final String message;

    EventCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }
    public String message() {
        return message;
    }

    public static EventCode fromCode(String code) {
        for (EventCode e : values()) {
            if (e.code.equals(code)) return e;
        }
        throw new IllegalArgumentException("Código de evento desconhecido: " + code);
    }
}