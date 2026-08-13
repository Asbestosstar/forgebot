package nl.finnt730.paste;

public final class CAPaste extends MCLogs {
    @Override
    public String getResultURL(String content) {
        String sup = super.getResultURL(content);
        if (sup == null) return null;
        return sup.replace("mclo.gs/", "kostromdan.dev/paste/mclogs/");
    }

    @Override
    public String getId() {
        return "capaste";
    }
}
