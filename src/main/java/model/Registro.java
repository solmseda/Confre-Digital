package model;

import java.time.LocalDateTime;

public class Registro {
    private Long rid;
    private LocalDateTime timestamp;
    private Integer mid;
    private Integer uid;
    private String detalhes;

    public Registro() {}
    public Long getRid() { return rid; }
    public void setRid(Long rid) { this.rid = rid; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Integer getMid() { return mid; }
    public void setMid(Integer mid) { this.mid = mid; }
    public Integer getUid() { return uid; }
    public void setUid(Integer uid) { this.uid = uid; }
    public String getDetalhes() { return detalhes; }
    public void setDetalhes(String detalhes) { this.detalhes = detalhes; }
}