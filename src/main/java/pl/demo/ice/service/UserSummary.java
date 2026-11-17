package pl.demo.ice.service;

public class UserSummary {
    private final String login;
    private final String imie;
    private final String nazwisko;

    public UserSummary(String login, String imie, String nazwisko) {
        this.login = login;
        this.imie = imie;
        this.nazwisko = nazwisko;
    }

    public String getLogin() { return login; }
    public String getImie() { return imie; }
    public String getNazwisko() { return nazwisko; }
}
