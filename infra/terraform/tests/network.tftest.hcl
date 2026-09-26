# All operations use a mocked AWS provider: no credentials or AWS resources.
mock_provider "aws" {
  source = "./tests"
}

run "network_boundaries" {
  command = apply

  assert {
    condition = (
      length(aws_subnet.public) == 2 && length(aws_subnet.private) == 2 &&
      keys(aws_subnet.public) == keys(aws_subnet.private) &&
      aws_vpc.main.enable_dns_support && aws_vpc.main.enable_dns_hostnames
    )
    error_message = "The default network must span two AZs with DNS enabled."
  }

  assert {
    condition = alltrue([
      for az, subnet in aws_subnet.private :
      !subnet.map_public_ip_on_launch &&
      subnet.availability_zone == az &&
      aws_route_table_association.private[az].subnet_id == subnet.id &&
      aws_route_table_association.private[az].route_table_id == aws_route_table.private[az].id &&
      length(aws_route_table.private[az].route) == 0
    ])
    error_message = "Private subnets must use their private route tables without internet routes or public IP assignment."
  }

  assert {
    condition = (
      aws_route.public_internet.destination_cidr_block == "0.0.0.0/0" &&
      aws_route.public_internet.gateway_id == aws_internet_gateway.public.id &&
      aws_route.public_internet.route_table_id == aws_route_table.public.id &&
      alltrue([
        for az, subnet in aws_subnet.public :
        !subnet.map_public_ip_on_launch && subnet.availability_zone == az &&
        aws_route_table_association.public[az].subnet_id == subnet.id &&
        aws_route_table_association.public[az].route_table_id == aws_route_table.public.id
      ])
    )
    error_message = "Only public subnet route tables may use the internet gateway."
  }

  assert {
    condition = (
      aws_vpc_security_group_ingress_rule.alb_https.security_group_id == aws_security_group.alb.id &&
      aws_vpc_security_group_ingress_rule.alb_https.cidr_ipv4 == "0.0.0.0/0" &&
      aws_vpc_security_group_ingress_rule.alb_https.ip_protocol == "tcp" &&
      aws_vpc_security_group_ingress_rule.alb_https.from_port == 443 &&
      aws_vpc_security_group_ingress_rule.alb_https.to_port == 443
    )
    error_message = "The ALB public ingress must be limited to HTTPS."
  }

  assert {
    condition = (
      aws_vpc_security_group_ingress_rule.ecs_from_alb.security_group_id == aws_security_group.ecs.id &&
      aws_vpc_security_group_ingress_rule.ecs_from_alb.referenced_security_group_id == aws_security_group.alb.id &&
      aws_vpc_security_group_ingress_rule.ecs_from_alb.cidr_ipv4 == null &&
      aws_vpc_security_group_ingress_rule.ecs_from_alb.cidr_ipv6 == null &&
      aws_vpc_security_group_ingress_rule.ecs_from_alb.ip_protocol == "tcp" &&
      aws_vpc_security_group_ingress_rule.ecs_from_alb.from_port == 8080 &&
      aws_vpc_security_group_ingress_rule.ecs_from_alb.to_port == 8080 &&
      aws_vpc_security_group_egress_rule.alb_to_ecs.security_group_id == aws_security_group.alb.id &&
      aws_vpc_security_group_egress_rule.alb_to_ecs.referenced_security_group_id == aws_security_group.ecs.id &&
      aws_vpc_security_group_egress_rule.alb_to_ecs.from_port == 8080 &&
      aws_vpc_security_group_egress_rule.alb_to_ecs.to_port == 8080
    )
    error_message = "Backend traffic must be limited to ALB-to-ECS on TCP 8080."
  }

  assert {
    condition = (
      aws_vpc_security_group_ingress_rule.rds_from_ecs.security_group_id == aws_security_group.rds.id &&
      aws_vpc_security_group_ingress_rule.rds_from_ecs.referenced_security_group_id == aws_security_group.ecs.id &&
      aws_vpc_security_group_ingress_rule.rds_from_ecs.cidr_ipv4 == null &&
      aws_vpc_security_group_ingress_rule.rds_from_ecs.cidr_ipv6 == null &&
      aws_vpc_security_group_ingress_rule.rds_from_ecs.ip_protocol == "tcp" &&
      aws_vpc_security_group_ingress_rule.rds_from_ecs.from_port == 5432 &&
      aws_vpc_security_group_ingress_rule.rds_from_ecs.to_port == 5432 &&
      aws_vpc_security_group_egress_rule.ecs_to_rds.security_group_id == aws_security_group.ecs.id &&
      aws_vpc_security_group_egress_rule.ecs_to_rds.referenced_security_group_id == aws_security_group.rds.id &&
      aws_vpc_security_group_egress_rule.ecs_to_rds.from_port == 5432 &&
      aws_vpc_security_group_egress_rule.ecs_to_rds.to_port == 5432 &&
      length(aws_security_group.rds.egress) == 0
    )
    error_message = "PostgreSQL traffic must be limited to ECS-to-RDS on TCP 5432."
  }

  assert {
    condition = (
      aws_vpc_security_group_egress_rule.ecs_https.security_group_id == aws_security_group.ecs.id &&
      aws_vpc_security_group_egress_rule.ecs_https.cidr_ipv4 == "0.0.0.0/0" &&
      aws_vpc_security_group_egress_rule.ecs_https.ip_protocol == "tcp" &&
      aws_vpc_security_group_egress_rule.ecs_https.from_port == 443 &&
      aws_vpc_security_group_egress_rule.ecs_https.to_port == 443
    )
    error_message = "Public-IP Fargate tasks need outbound HTTPS for AWS services, without opening other internet ports."
  }

  assert {
    condition = (
      length(aws_default_security_group.default.ingress) == 0 &&
      length(aws_default_security_group.default.egress) == 0 &&
      output.vpc_id == aws_vpc.main.id &&
      toset(keys(output.private_subnet_ids)) == toset(keys(var.subnets)) &&
      toset(keys(output.public_subnet_ids)) == toset(keys(var.subnets)) &&
      output.security_group_ids.alb == aws_security_group.alb.id &&
      output.security_group_ids.ecs == aws_security_group.ecs.id &&
      output.security_group_ids.rds == aws_security_group.rds.id
    )
    error_message = "Default SG must deny traffic and outputs must identify the intended resources."
  }
}

run "reject_single_az" {
  command = plan
  variables {
    subnets = {
      eu-north-1a = { public_cidr = "10.20.0.0/24", private_cidr = "10.20.10.0/24" }
    }
  }
  expect_failures = [var.subnets]
}

run "reject_wrong_region" {
  command = plan
  variables {
    region = "eu-west-1"
  }
  expect_failures = [var.subnets]
}

run "reject_invalid_cidr" {
  command = plan
  variables {
    vpc_cidr = "not-a-cidr"
  }
  expect_failures = [var.vpc_cidr]
}
