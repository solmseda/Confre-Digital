package model;
/*
 * Trabalho 3 de Segurança da Informação
 * Sol Castilho Araújo de Moraes Sêda - 2511704
 * Leonardo Giuri Santiago - 2410725
 */

import java.time.LocalDateTime;

public class Chaveiro {
    private Integer kid;
    private Integer uid;
    private String certPem;
    private byte[] privateKeyEnc;
    private LocalDateTime criadoEm;

    public Chaveiro() {}

    public Integer getKid() {
        return kid;
    }

    public void setKid(Integer kid) {
        this.kid = kid;
    }

    public Integer getUid() {
        return uid;
    }

    public void setUid(Integer uid) {
        this.uid = uid;
    }

    public String getCertPem() {
        return certPem;
    }

    public void setCertPem(String certPem) {
        this.certPem = certPem;
    }

    public byte[] getPrivateKeyEnc() {
        return privateKeyEnc;
    }

    public void setPrivateKeyEnc(byte[] privateKeyEnc) {
        this.privateKeyEnc = privateKeyEnc;
    }
}