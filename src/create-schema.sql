set foreign_key_checks = 0;

drop table Cookies;
drop table RawMaterials;
drop table Customers;
drop table Recipes;
drop table Orders;
drop table NbrPallets;
drop table Pallets;
drop table Status;

set foreign_key_checks = 1;



create table Cookies (
cookieName varchar(30) primary key );


create table RawMaterials
(
    materialName            varchar(30) primary key,
    amount                  int,
    unit                    varchar(5),
    lastModified            DATE,
    lastAmount              int
);

create table Customers
(
    name            varchar(30) primary key,
    adress          varchar(30)
);

create table Recipes
(
    cookieName            varchar(30),
    materialName          varchar(30),
    amount                  int,
    primary key (cookieName, materialName),
    foreign key (cookieName) references Cookies(cookieName),
    foreign key (materialName) references RawMaterials(materialName)
);

create table Orders
(
    orderId              integer primary key auto_increment,
    name                  varchar(30),
    deliveryDate          DATE,
    foreign key (name) references Customers(name)
);

create table NbrPallets
(
    orderId               integer,
    cookieName            varchar(30),
    nbrPallet             integer,
    primary key (cookieName, orderId),
    foreign key (cookieName) references Cookies(cookieName),
    foreign key (orderId) references Orders(orderId)
);

create table Pallets
(
    palletId              integer primary key auto_increment,
    cookieName            varchar(30),
    productionDate        date,
    isBlocked             int check (isBlocked=0 or isBlocked=1),
    location              varchar(30),
    foreign key (cookieName) references Cookies(cookieName)
);

create table Status
(
    palletId              integer,
    orderId               integer,
    Date                  DATE,
    isDelivered           int check (isDelivered=0 or isDelivered=1),
    primary key (palletId, orderId),
    foreign key (palletId) references Pallets(palletId),
    foreign key (orderId) references Orders(orderId)
);