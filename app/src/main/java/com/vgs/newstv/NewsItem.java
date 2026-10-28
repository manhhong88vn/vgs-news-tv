package com.vgs.newstv;

public class NewsItem {
    private String title;
    private String link;
    private String imageUrl;
    private String description;

    public NewsItem(String title, String link, String imageUrl, String description) {
        this.title = title;
        this.link = link;
        this.imageUrl = imageUrl;
        this.description = description;
    }

    public String getTitle() { return title; }
    public String getLink() { return link; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
}
