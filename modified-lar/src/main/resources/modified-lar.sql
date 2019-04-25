CREATE TABLE hmda_user.modifiedlar2018
(
    id integer NOT NULL,
    lei character varying NOT NULL,
    loan_type integer,
    loan_purpose integer,
    preapproval integer,
    construction_method character varying,
    occupancy_type integer,
    loan_amount numeric,
    action_taken_type integer,
    state character varying,
    county character varying,
    tract character varying,
    ethnicity_applicant_1 integer,
    ethnicity_applicant_2 integer,
    ethnicity_applicant_3 integer,
    ethnicity_applicant_4 integer,
    ethnicity_applicant_5 integer,
    ethnicity_observed_applicant int,
    ethnicity_co_applicant_1 integer,
    ethnicity_co_applicant_2 integer,
    ethnicity_co_applicant_3 integer,
    ethnicity_co_applicant_4 integer,
    ethnicity_co_applicant_5 integer,
    ethnicity_observed_co_applicant int,
    race_applicant_1 integer,
    race_applicant_2 integer,
    race_applicant_3 integer,
    race_applicant_4 integer,
    race_applicant_5 integer,
    race_co_applicant_1 integer,
    race_co_applicant_2 integer,
    race_co_applicant_3 integer,
    race_co_applicant_4 integer,
    race_co_applicant_5 integer,
    race_observed_applicant integer,
    race_observed_co_applicant integer,
    sex_applicant integer,
    sex_co_applicant integer,
    observed_sex_applicant integer,
    observed_sex_co_applicant integer,
    age_applicant character varying,
    applicant_age_greater_than_62 character varying,
    age_co_applicant character varying,
    coapplicant_age_greater_than_62 character varying,
    income character varying,
    purchaser_type integer,
    rate_spread character varying,
    hoepa_status integer,
    lien_status integer,
    credit_score_type_applicant integer,
    credit_score_type_co_applicant integer,
    denial_reason1 integer,
    denial_reason2 integer,
    denial_reason3 integer,
    denial_reason4 integer,
    total_loan_costs character varying,
    total_points character varying,
    origination_charges character varying,
    discount_points character varying,
    lender_credits character varying,
    interest_rate character varying,
    payment_penalty character varying,
    debt_to_incode character varying,
    loan_value_ratio character  varying,
    loan_term character varying,
    rate_spread_intro character varying,
    baloon_payment integer,
    insert_only_payment integer,
    amortization integer,
    other_amortization integer,
    property_value character varying,
    home_security_policy integer,
    lan_property_interest integer,
    total_units character varying,
    mf_affordable character varying,
    application_submission integer,
    payable integer,
    aus1 integer,
    aus2 integer,
    aus3 integer,
    aus4 integer,
    aus5 integer,
    reverse_mortgage integer,
    line_of_credits integer,
    business_or_commercial integer,
    population character varying,
    minority_population_percent character varying,
    ffiec_med_fam_income character varying,
    tract_to_msamd character varying,
    owner_occupied_units character varying,
    one_to_four_fam_units character varying,
    msa_md integer,
    msa_md_name character varying,
    loan_flag character varying,
    created_at timestamp default current_timestamp,
    submission_id character varying
    filing_year integer,
    conforming_loan_limit character varying
    median_age integer,
    median_age_calculated integer,
    median_income_percentage integer,
    percent_median_msa_income integer
)
WITH (
    OIDS = FALSE
);

ALTER TABLE public.modifiedlar2018
  OWNER to postgres;

ALTER TABLE  public.modifiedlar2018
  ADD COLUMN race_categorization character varying,
  ADD COLUMN sex_categorization character varying;

CREATE INDEX ON modifiedlar2018 (lei);