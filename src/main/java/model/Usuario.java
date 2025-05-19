/*
 * Trabalho 3 de Segurança da Informação
 * Sol Castilho Araújo de Moraes Sêda - 2511704
 * Leonardo Giuri Santiago - 2410725
 */

package model;

import java.time.LocalDateTime;

public class Usuario {
    private Integer uid;
    private String loginEmail;
    private String nome;
    private String senhaBcrypt;
    private byte[] totpSecretEnc;
    private Integer gid;
    private Integer kid;  // novo campo para armazenar o KID do Chaveiro
    private LocalDateTime criadoEm;

    // getters & setters
    public Integer getUid() { return uid; }
    public void setUid(Integer uid) { this.uid = uid; }

    public String getLoginEmail() { return loginEmail; }
    public void setLoginEmail(String loginEmail) { this.loginEmail = loginEmail; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getSenhaBcrypt() { return senhaBcrypt; }
    public void setSenhaBcrypt(String senhaBcrypt) { this.senhaBcrypt = senhaBcrypt; }

    public byte[] getTotpSecretEnc() { return totpSecretEnc; }
    public void setTotpSecretEnc(byte[] totpSecretEnc) { this.totpSecretEnc = totpSecretEnc; }

    public Integer getGid() { return gid; }
    public void setGid(Integer gid) { this.gid = gid; }

    public Integer getKid() { return kid; }
    public void setKid(Integer kid) { this.kid = kid; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
