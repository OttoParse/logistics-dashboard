package pt.itm.cutter;

import org.json.JSONObject;

final class Models {
    private Models() {}

    static final class Category {
        int id;
        String name = "";
        String icon = "";
    }

    static final class Family {
        int id;
        int classOneId;
        String name = "";
        String icon = "";
    }

    static final class Product {
        int id;
        int classOneId;
        int classTwoId;
        String name = "";
        String cover = "";
        String detail = "";
        String file = "";
        String size = "";
        int isBack;
        JSONObject raw;
    }
}
