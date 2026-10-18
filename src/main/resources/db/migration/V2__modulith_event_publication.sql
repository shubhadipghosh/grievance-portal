create table event_publication (
                                   completion_attempts integer not null,
                                   completion_date timestamp(6) with time zone,
                                   last_resubmission_date timestamp(6) with time zone,
                                   publication_date timestamp(6) with time zone not null,
                                   id uuid not null,
                                   event_type varchar(255) not null,
                                   listener_id varchar(255) not null,
                                   serialized_event varchar(255) not null,
                                   status varchar(255) check ((status in ('PUBLISHED','PROCESSING','COMPLETED','FAILED','RESUBMITTED'))),
                                   primary key (id)
);