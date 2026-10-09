import * as readline from "readline";

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

interface Employee {
    id: number;
    name: string;
    department: string;
    salary: number;
}

let emp: Employee[] = [];

function menu() {
    console.log(`
1.Add  2.View  3.Update  4.Delete  5.Exit`);

    rl.question("Choice: ", c => {
        if (c === "1") {
            rl.question("ID: ", id =>
            rl.question("Name: ", name =>
            rl.question("Dept: ", department =>
            rl.question("Salary: ", salary => {
                emp.push({ id:+id, name, department, salary:+salary });
                console.log("Added!");
                menu();
            }))));
        }

        else if (c === "2") {
            emp.length
                ? emp.forEach(e => console.log(e))
                : console.log("No employees!");
            menu();
        }

        else if (c === "3") {
            rl.question("ID: ", id => {
                let e = emp.find(x => x.id === +id);

                if (!e) {
                    console.log("Not found!");
                    return menu();
                }

                rl.question("Name: ", n =>
                rl.question("Dept: ", d =>
                rl.question("Salary: ", s => {
                    e.name = n;
                    e.department = d;
                    e.salary = +s;
                    console.log("Updated!");
                    menu();
                })));
            });
        }

        else if (c === "4") {
            rl.question("ID: ", id => {
                let i = emp.findIndex(e => e.id === +id);

                if (i < 0) console.log("Not found!");
                else {
                    emp.splice(i, 1);
                    console.log("Deleted!");
                }
                menu();
            });
        }

        else if (c === "5") {
            rl.close();
        }

        else {
            console.log("Invalid!");
            menu();
        }
    });
}

menu();
