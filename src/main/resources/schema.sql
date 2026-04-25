create table if not exists cart
(
    id         varchar(255) primary key,
    version    bigint default 0
    );

create table if not exists product
(
    id      bigint AUTO_INCREMENT primary key,
    title   varchar(255) not null,
    description varchar(500) not null,
    img_path  varchar(255) not null,
    price     bigint not null,
    version   bigint default 0
    );

 create table if not exists orders
 (
     id         bigint AUTO_INCREMENT primary key,
     version    bigint default 0
     );

 create table if not exists cart_item
 (
     id        bigint AUTO_INCREMENT primary key,
     cart_id   varchar(255) not null,
     product_id bigint not null,
     quantity  int not null,
     version   bigint default 0,
     constraint fk_cart_item_cart
     foreign key (cart_id) references cart(id) on delete cascade,
     constraint fk_cart_item_product
     foreign key (product_id) references product(id) on delete cascade
     );

     create table if not exists order_item
      (
          id         bigint AUTO_INCREMENT primary key,
          order_id   bigint not null,
          product_id bigint not null,
          quantity  int not null,
          version   bigint default 0,
          constraint fk_order_item_order
          foreign key (order_id) references orders(id) on delete cascade,
          constraint fk_order_item_product
          foreign key (product_id) references product(id) on delete cascade
          );



