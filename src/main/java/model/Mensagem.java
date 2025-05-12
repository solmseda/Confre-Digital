package model;

public class Mensagem {
    private Integer mid;
    private String codigo;
    private String texto;

    public Mensagem() {}
    public Integer getMid() { return mid; }
    public void setMid(Integer mid) { this.mid = mid; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
}