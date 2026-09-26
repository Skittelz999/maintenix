variable "db_name" {
  description = "Initial PostgreSQL database name."
  type        = string
  default     = "maintenix"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9]{0,62}$", var.db_name))
    error_message = "Use 1-63 alphanumeric characters, starting with a letter."
  }
}

variable "db_username" {
  description = "RDS master username; RDS generates the password in Secrets Manager."
  type        = string
  default     = "maintenix_admin"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]{0,62}$", var.db_username)) && !contains(["admin", "root", "rdsadmin"], lower(var.db_username))
    error_message = "Use 1-63 letters, digits or underscores, starting with a letter; do not use admin, root or rdsadmin."
  }
}

variable "db_instance_class" {
  description = "Demo RDS instance size; verify availability and cost in the selected region."
  type        = string
  default     = "db.t4g.micro"

  validation {
    condition     = can(regex("^db\\.[a-z0-9]+\\.[a-z0-9]+$", var.db_instance_class))
    error_message = "Use an RDS instance class such as db.t4g.micro."
  }
}

variable "db_allocated_storage" {
  description = "Allocated gp3 storage in GiB; autoscaling is disabled to keep capacity explicit."
  type        = number
  default     = 20

  validation {
    condition     = var.db_allocated_storage >= 20 && var.db_allocated_storage <= 65536 && floor(var.db_allocated_storage) == var.db_allocated_storage
    error_message = "gp3 storage must be an integer between 20 and 65536 GiB."
  }
}

variable "project_name" {
  description = "Project prefix for resource names and tags."
  type        = string
  default     = "maintenix"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,31}$", var.project_name))
    error_message = "Use 1-32 lowercase letters, digits or hyphens, starting with a letter."
  }
}

variable "environment" {
  description = "Environment name; use separate Terraform state for each environment."
  type        = string
  default     = "prod"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,15}$", var.environment))
    error_message = "Use 1-16 lowercase letters, digits or hyphens, starting with a letter."
  }
}

variable "region" {
  description = "AWS region. Update subnet AZ names if changing this value."
  type        = string
  default     = "eu-north-1"
}

variable "vpc_cidr" {
  description = "IPv4 VPC CIDR. All subnet CIDRs must fit within it without overlapping."
  type        = string
  default     = "10.20.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "vpc_cidr must be a valid IPv4 CIDR."
  }
}

variable "subnets" {
  description = "Public/private IPv4 CIDR pair per AZ; at least two distinct AZs in the selected region."
  type = map(object({
    public_cidr  = string
    private_cidr = string
  }))
  default = {
    eu-north-1a = { public_cidr = "10.20.0.0/24", private_cidr = "10.20.10.0/24" }
    eu-north-1b = { public_cidr = "10.20.1.0/24", private_cidr = "10.20.11.0/24" }
  }

  validation {
    condition     = length(var.subnets) >= 2
    error_message = "Provide subnet pairs in at least two availability zones."
  }

  validation {
    condition     = alltrue([for az in keys(var.subnets) : can(regex("^${var.region}[a-z]$", az))])
    error_message = "Subnet keys must be standard availability zones in the selected AWS region."
  }

  validation {
    condition = alltrue([
      for subnet in values(var.subnets) :
      can(cidrnetmask(subnet.public_cidr)) && can(cidrnetmask(subnet.private_cidr))
    ])
    error_message = "All public and private subnet CIDRs must be valid IPv4 CIDRs."
  }
}
