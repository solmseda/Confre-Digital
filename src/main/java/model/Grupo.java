package model;

import java.time.LocalDateTime;

public class Grupo {
    private Integer gid;
    private String nomeGrupo;

    public Grupo() {}

    public Integer getGid() {
        return gid;
    }

    public void setGid(Integer gid) {
        this.gid = gid;
    }

    public String getNomeGrupo() {
        return nomeGrupo;
    }

    public void setNomeGrupo(String nomeGrupo) {
        this.nomeGrupo = nomeGrupo;
    }
}