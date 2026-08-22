
    create table app_settings (
        setting_key varchar(50) not null,
        setting_value varchar(100) not null,
        primary key (setting_key)
    ) engine=InnoDB;

    create table comments (
        created_at datetime(6),
        id bigint not null auto_increment,
        market_id bigint not null,
        parent_id bigint,
        user_id bigint not null,
        image_url varchar(500),
        content TEXT not null,
        primary key (id)
    ) engine=InnoDB;

    create table market_images (
        sort_order integer not null,
        id bigint not null auto_increment,
        market_id bigint not null,
        image_url varchar(500) not null,
        primary key (id)
    ) engine=InnoDB;

    create table markets (
        is_closed bit default false not null,
        scrap_count integer default 0 not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        updated_at datetime(6),
        user_id bigint not null,
        title varchar(100) not null,
        item_categories varchar(200),
        category varchar(255) not null check (category in ('KPOP','TWO_D','MUSICAL','ETC')),
        description TEXT not null,
        primary key (id)
    ) engine=InnoDB;

    create table refresh_tokens (
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        user_id bigint not null,
        token varchar(500) not null,
        primary key (id)
    ) engine=InnoDB;

    create table scraps (
        created_at datetime(6),
        id bigint not null auto_increment,
        market_id bigint not null,
        user_id bigint not null,
        primary key (id)
    ) engine=InnoDB;

    create table users (
        marketing_email_agreed bit default false not null,
        marketing_sns_agreed bit default false not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        terms_agreed_at datetime(6),
        updated_at datetime(6),
        nickname varchar(50) not null,
        provider_id varchar(100) not null,
        profile_image_url varchar(500),
        provider varchar(255) not null check (provider in ('KAKAO','GOOGLE')),
        primary key (id)
    ) engine=InnoDB;

    alter table markets 
       add constraint uk_markets_user_id unique (user_id);

    alter table refresh_tokens 
       add constraint uk_refresh_tokens_user_id unique (user_id);

    alter table scraps 
       add constraint uk_scraps_user_id_market_id unique (user_id, market_id);

    alter table users 
       add constraint uk_users_provider_provider_id unique (provider, provider_id);

    alter table comments 
       add constraint FKsn6uugpr36lf3mh4s80mietnp 
       foreign key (market_id) 
       references markets (id);

    alter table comments 
       add constraint FKlri30okf66phtcgbe5pok7cc0 
       foreign key (parent_id) 
       references comments (id);

    alter table comments 
       add constraint FK8omq0tc18jd43bu5tjh6jvraq 
       foreign key (user_id) 
       references users (id);

    alter table market_images 
       add constraint FKgwbygvuyg889rf9o8j7m5ih8h 
       foreign key (market_id) 
       references markets (id);

    alter table markets 
       add constraint FKms5b3gbxr0dkxtdn9m76esqih 
       foreign key (user_id) 
       references users (id);

    alter table refresh_tokens 
       add constraint FK1lih5y2npsf8u5o3vhdb9y0os 
       foreign key (user_id) 
       references users (id);

    alter table scraps 
       add constraint FKeg8yknnyqijf9bc3s0gpdohb2 
       foreign key (market_id) 
       references markets (id);

    alter table scraps 
       add constraint FKqd3nh3tj8ru0ubnk54qckuh42 
       foreign key (user_id) 
       references users (id);
